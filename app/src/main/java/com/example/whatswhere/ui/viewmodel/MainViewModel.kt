package com.example.whatswhere.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.example.whatswhere.R
import com.example.whatswhere.data.*
import com.example.whatswhere.ui.adapter.ViewType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    BY_NAME_ASC, BY_NAME_DESC, BY_DATE_NEWEST, BY_DATE_OLDEST
}

data class ItemListState(
    val filteredAndSortedList: List<ItemWithTags>,
    val isDatabaseEmpty: Boolean
)

class MainViewModel(
    application: Application,
    private val itemDao: ItemDao
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryKey = MutableStateFlow("chip_all")
    val selectedCategoryKey: StateFlow<String> = _selectedCategoryKey.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.BY_NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val syncManager = SyncManager(application, itemDao)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage: SharedFlow<String> = _syncMessage.asSharedFlow()

    private val _allCategories = MutableStateFlow<List<Category>>(emptyList())
    val allCategories: StateFlow<List<Category>> = _allCategories.asStateFlow()

    private val _viewType = MutableStateFlow(ViewType.LIST)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()

    init {
        viewModelScope.launch {
            itemDao.getAllCategories().collect {
                _allCategories.value = it
            }
        }
        loadViewPreference()
    }

    val itemListState: StateFlow<ItemListState> =
        combine(
            itemDao.getAllItemsWithTags(),
            _searchQuery,
            _selectedCategoryKey,
            _sortOrder,
            _allCategories
        ) { items, query, selectedCategoryKey, sortOrder, categories ->

            val categoryMap = categories.associateBy { it.id }

            val filteredItems = items.filter { itemWithTags ->
                val item = itemWithTags.item
                val categoryOfItem = categoryMap[item.categoryId]

                val categoryMatch = selectedCategoryKey == "chip_all" || categoryOfItem?.nameKey == selectedCategoryKey

                val searchMatch = query.isBlank() ||
                        item.name.contains(query, ignoreCase = true) ||
                        item.location.contains(query, ignoreCase = true) ||
                        item.description?.contains(query, ignoreCase = true) == true ||
                        item.serialNumber?.contains(query, ignoreCase = true) == true ||
                        item.modelNumber?.contains(query, ignoreCase = true) == true ||
                        itemWithTags.tags.any { it.nameKey.contains(query, ignoreCase = true) }
                categoryMatch && searchMatch
            }

            val sortedItems = when (sortOrder) {
                SortOrder.BY_NAME_ASC -> filteredItems.sortedBy { it.item.name }
                SortOrder.BY_NAME_DESC -> filteredItems.sortedByDescending { it.item.name }
                SortOrder.BY_DATE_NEWEST -> filteredItems.sortedByDescending { it.item.createdAt }
                SortOrder.BY_DATE_OLDEST -> filteredItems.sortedBy { it.item.createdAt }
            }

            ItemListState(
                filteredAndSortedList = sortedItems,
                isDatabaseEmpty = items.isEmpty()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ItemListState(emptyList(), true)
        )

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onCategorySelected(categoryKey: String) { _selectedCategoryKey.value = categoryKey }
    fun onSortOrderSelected(sortOrder: SortOrder) { _sortOrder.value = sortOrder }

    fun toggleViewType() {
        _viewType.value = if (_viewType.value == ViewType.LIST) ViewType.GRID else ViewType.LIST
        saveViewPreference()
    }

    private fun saveViewPreference() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("view_type", _viewType.value.name).apply()
    }

    private fun loadViewPreference() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val viewTypeName = sharedPrefs.getString("view_type", ViewType.LIST.name)
        _viewType.value = ViewType.valueOf(viewTypeName ?: ViewType.LIST.name)
    }


    fun onRefresh(isManual: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            syncManager.syncLocalToCloud()
            when (val result = syncManager.syncCloudToLocal()) {
                is SyncResult.Success -> {
                    if (isManual) {
                        val successMessage = getApplication<Application>().getString(R.string.toast_sync_success)
                        _syncMessage.emit(successMessage)
                    }
                }
                is SyncResult.Error -> {
                    _syncMessage.emit(result.message)
                }
            }
            _isRefreshing.value = false
        }
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val itemDao: ItemDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, itemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}