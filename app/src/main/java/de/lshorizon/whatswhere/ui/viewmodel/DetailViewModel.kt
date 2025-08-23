package de.lshorizon.whatswhere.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.data.ItemDao
import de.lshorizon.whatswhere.data.CategoryRepository
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val itemDao: ItemDao, private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _itemDetails = MutableStateFlow<Item?>(null)
    val itemDetails: StateFlow<Item?> = _itemDetails.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

class DetailViewModelFactory(private val itemDao: ItemDao, private val categoryRepository: CategoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(itemDao, categoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
