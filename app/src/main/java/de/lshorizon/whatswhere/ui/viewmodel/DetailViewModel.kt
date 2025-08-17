package de.lshorizon.whatswhere.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.data.ItemDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(private val itemDao: ItemDao) : ViewModel() {

    private val _itemDetails = MutableStateFlow<Item?>(null)
    val itemDetails: StateFlow<Item?> = _itemDetails.asStateFlow()

    fun loadItemDetails(itemId: String) {
        viewModelScope.launch {
            itemDao.getItem(itemId).collect {
                _itemDetails.value = it
            }
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            itemDao.delete(item)
        }
    }
}

class DetailViewModelFactory(private val itemDao: ItemDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(itemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
