package com.example.whatswhere.ui.viewmodel

import androidx.lifecycle.*
import com.example.whatswhere.data.*
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import com.example.whatswhere.InventoryApp

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

            val itemToSave = item.copy(
                userId = user.uid,
                category = categoryName, // Use the category name from UI
                categoryResourceId = categoryResourceId, // Set the resource ID
                needsSync = true
            )

            if (itemToSave.id.isEmpty()) {
                val newDocRef = FirestoreManager.getItemsCollection().document()
                itemToSave.id = newDocRef.id
            }

            itemDao.insert(itemToSave)

            try {
                FirestoreManager.saveItem(itemToSave)
                itemDao.insert(itemToSave.copy(needsSync = false))
            } catch (e: Exception) {
                e.printStackTrace()
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
