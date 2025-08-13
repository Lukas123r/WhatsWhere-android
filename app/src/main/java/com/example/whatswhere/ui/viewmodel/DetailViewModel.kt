// Pfad: app/src/main/java/com/example/whatswhere/ui/viewmodel/DetailViewModel.kt
package com.example.whatswhere.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whatswhere.data.FullItemDetails
import com.example.whatswhere.data.ItemDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(private val itemDao: ItemDao, private val itemId: String) : ViewModel() {

    val fullDetails: StateFlow<FullItemDetails?> = itemDao.getItemWithTags(itemId)
        .filterNotNull()
        .flatMapLatest { itemWithTags ->
            itemDao.getCategory(itemWithTags.item.categoryId).map { category ->
                FullItemDetails(itemWithTags, category)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    fun deleteItem() {
        viewModelScope.launch {
            fullDetails.value?.let { details ->
                itemDao.delete(details.itemWithTags.item)
            }
        }
    }

    fun lendItem(lentTo: String, returnDate: Long) {
        viewModelScope.launch {
            itemDao.updateLendingStatus(itemId, true, lentTo, returnDate)
        }
    }

    fun returnItem() {
        viewModelScope.launch {
            itemDao.updateLendingStatus(itemId, false, null, null)
        }
    }
}

class DetailViewModelFactory(private val itemDao: ItemDao, private val itemId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(itemDao, itemId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}