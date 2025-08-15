package com.example.whatswhere.ui.viewmodel

import androidx.lifecycle.*
import com.example.whatswhere.data.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddItemViewModel(private val itemDao: ItemDao) : ViewModel() {

    private val _itemToEdit = MutableStateFlow<Item?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

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

    fun saveOrUpdateItem(item: Item) {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user == null) return@launch

            val itemToSave = item.copy(
                userId = user.uid,
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

class AddItemViewModelFactory(private val itemDao: ItemDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddItemViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddItemViewModel(itemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
