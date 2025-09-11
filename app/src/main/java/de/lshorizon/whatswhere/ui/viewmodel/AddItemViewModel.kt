package de.lshorizon.whatswhere.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.lshorizon.whatswhere.data.CategoryRepository
import de.lshorizon.whatswhere.data.FirestoreManager
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.data.ItemDao
import de.lshorizon.whatswhere.data.dao.Category
import de.lshorizon.whatswhere.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddItemViewModel(application: Application, private val itemDao: ItemDao, private val categoryRepository: CategoryRepository) : AndroidViewModel(application) {

    private val _itemToEdit = MutableStateFlow<Item?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    val categories = categoryRepository.getCategories()
        .map { list ->
            val ctx = getApplication<Application>()
            val bad1 = ctx.getString(R.string.category_saved)
            val bad2 = ctx.getString(R.string.category_saved_offline_sync_later)
            list.filterNot { c ->
                c.resourceId == R.string.category_saved ||
                c.resourceId == R.string.category_saved_offline_sync_later ||
                c.name.equals(bad1, ignoreCase = true) ||
                c.name.equals(bad2, ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            itemDao.getItem(itemId).collect {
                _itemToEdit.value = it
            }
        }
    }

    fun clearItemToEdit() {
        _itemToEdit.value = null
    }

    fun addCategory(category: Category) {
        viewModelScope.launch {
            // Canonicalize input: map localized default labels to default keys
            val raw = category.name.trim()
            if (raw.isEmpty()) return@launch

            val app = getApplication<Application>()
            val defaultKey = de.lshorizon.whatswhere.util.CategoryLocaleMapper.resolveKeyFromText(app, raw)

            // Guard: ignore accidental status/toast texts
            val bad1 = app.getString(R.string.category_saved)
            val bad2 = app.getString(R.string.category_saved_offline_sync_later)
            if (raw.equals(bad1, true) || raw.equals(bad2, true)) return@launch

            // Do not add any predefined default category (including "all"): they already exist
            if (defaultKey != null) return@launch

            val canonicalName = raw
            val resId = 0

            val toSave = category.copy(name = canonicalName, resourceId = resId)
            categoryRepository.addCategory(toSave)
        }
    }

    fun saveOrUpdateItem(item: Item, categoryName: String) { // Add categoryName parameter
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user == null) return@launch

            val selectedCategoryObject = categories.value.find { it.name == categoryName || (it.resourceId != 0 && getApplication<Application>().getString(it.resourceId) == categoryName) }
            val categoryResourceId = selectedCategoryObject?.resourceId ?: 0
            val canonicalCategoryName = selectedCategoryObject?.name ?: categoryName // store canonical key when possible

            var itemToSave = item.copy(
                userId = user.uid,
                category = canonicalCategoryName, // store canonical key when known
                categoryResourceId = categoryResourceId, // Set the resource ID
                needsSync = true
            )

            if (itemToSave.id.isEmpty()) {
                val newDocRef = FirestoreManager.getItemsCollection().document()
                itemToSave = itemToSave.copy(id = newDocRef.id)
            }

            itemDao.insert(itemToSave)

            try {
                FirestoreManager.saveItem(itemToSave)
                itemDao.insert(itemToSave.copy(needsSync = false))
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.toast_sync_error_generic, e.message ?: ""),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

class AddItemViewModelFactory(private val application: Application, private val itemDao: ItemDao, private val categoryRepository: CategoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddItemViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddItemViewModel(application, itemDao, categoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
