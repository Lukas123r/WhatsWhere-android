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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddItemViewModel(application: Application, private val itemDao: ItemDao, private val categoryRepository: CategoryRepository) : AndroidViewModel(application) {

    private val _itemToEdit = MutableStateFlow<Item?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    val categories = categoryRepository.getCategories()
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
            categoryRepository.addCategory(category)
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
