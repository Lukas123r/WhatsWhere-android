package com.example.whatswhere.ui.viewmodel

import android.util.Log
import androidx.lifecycle.*
import com.example.whatswhere.data.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddItemViewModel(private val itemDao: ItemDao) : ViewModel() {

    val allTags: Flow<List<Tag>> = itemDao.getAllTags()
    val allCategories: Flow<List<Category>> = itemDao.getAllCategories()

    private val _itemToEdit = MutableStateFlow<ItemWithTags?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            itemDao.getItemWithTags(itemId).collect {
                _itemToEdit.value = it
            }
        }
    }

    fun clearItemToEdit() {
        _itemToEdit.value = null
    }

    fun saveOrUpdateItem(item: Item, selectedTagIds: List<Long>) {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user == null) return@launch

            // KORREKTUR: Baut den String aus den nameKeys zusammen.
            val tagKeysString = itemDao.getAllTags().first()
                .filter { selectedTagIds.contains(it.id) }
                .joinToString(";") { it.nameKey }

            val itemToSave = item.copy(
                userId = user.uid,
                tagsString = tagKeysString,
                needsSync = true
            )

            if (itemToSave.id.isEmpty()) {
                val newDocRef = FirestoreManager.getItemsCollection().document()
                itemToSave.id = newDocRef.id
            }

            itemDao.insert(itemToSave)
            itemDao.deleteTagsByItemId(itemToSave.id)
            selectedTagIds.forEach { tagId ->
                itemDao.insertItemTagCrossRef(ItemTagCrossRef(itemId = itemToSave.id, tagId = tagId))
            }

            try {
                FirestoreManager.saveItem(itemToSave)
                itemDao.insert(itemToSave.copy(needsSync = false))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createNewTag(tagName: String) {
        viewModelScope.launch {
            Log.d("AddItemViewModel", "Attempting to create new tag: $tagName")
            val newTag = Tag(nameKey = tagName)
            try {
                val tagId = itemDao.insertTag(newTag)
                Log.d("AddItemViewModel", "Tag '$tagName' inserted with ID: $tagId")
            } catch (e: Exception) {
                Log.e("AddItemViewModel", "Error inserting tag '$tagName': ${e.message}", e)
            }
        }
    }

    fun createNewCategory(categoryName: String) {
        viewModelScope.launch {
            val newCategoryId = itemDao.insertCategory(Category(nameKey = categoryName))
            val newCategory = Category(id = newCategoryId, nameKey = categoryName)
            FirestoreManager.saveCategory(newCategory)
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