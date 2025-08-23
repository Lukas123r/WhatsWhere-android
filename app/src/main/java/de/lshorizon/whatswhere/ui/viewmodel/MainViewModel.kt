package de.lshorizon.whatswhere.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.*
import de.lshorizon.whatswhere.ui.adapter.ViewType
import de.lshorizon.whatswhere.data.dao.CategoryDao
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import de.lshorizon.whatswhere.data.CategoryRepository

enum class SortOrder {
    BY_NAME_ASC, BY_NAME_DESC, BY_DATE_NEWEST, BY_DATE_OLDEST
}

data class ItemListState(
    val filteredAndSortedList: List<Item>,
    val isDatabaseEmpty: Boolean
)

class MainViewModel(
    application: Application,
    private val itemDao: ItemDao,
    private val categoryRepository: CategoryRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.BY_NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    private val _category = MutableStateFlow("All")

    val categories: StateFlow<List<Category>> = categoryRepository.getCategories()
        .map { categories ->
            val allCategory = Category("all", R.string.category_all)
            val sortedCategories = categories
                .filter { category ->
                    val isAllCategoryByName = category.name.lowercase() == "all"
                    val isAllCategoryByResourceId = category.resourceId == R.string.category_all
                    !(isAllCategoryByName || isAllCategoryByResourceId)
                }
                .sortedBy { 
                    if (it.resourceId != 0) {
                        getApplication<Application>().getString(it.resourceId).lowercase()
                    } else {
                        it.name.lowercase()
                    }
                }
            val finalCategories = mutableListOf<Category>()
            finalCategories.add(allCategory)
            finalCategories.addAll(sortedCategories)
            finalCategories
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val syncManager = SyncManager(application, itemDao)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage: SharedFlow<String> = _syncMessage.asSharedFlow()

    private val _viewType = MutableStateFlow(ViewType.LIST)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()

    init {
        loadViewPreference()
        onRefresh(isManual = false)
    }

    val itemListState: StateFlow<ItemListState> =
        combine(
            itemDao.getAllItems(),
            _searchQuery,
            _sortOrder,
            _category
        ) { items, query, sortOrder, category ->

            val filteredItems = items.filter { item ->
                val matchesCategory = category == "All" || item.category == category
                val matchesQuery = query.isBlank() ||
                        item.name.contains(query, ignoreCase = true) ||
                        item.location.contains(query, ignoreCase = true) ||
                        item.description?.contains(query, ignoreCase = true) == true ||
                        item.serialNumber?.contains(query, ignoreCase = true) == true ||
                        item.modelNumber?.contains(query, ignoreCase = true) == true
                matchesCategory && matchesQuery
            }

            val sortedItems = when (sortOrder) {
                SortOrder.BY_NAME_ASC -> filteredItems.sortedBy { it.name }
                SortOrder.BY_NAME_DESC -> filteredItems.sortedByDescending { it.name }
                SortOrder.BY_DATE_NEWEST -> filteredItems.sortedByDescending { it.createdAt }
                SortOrder.BY_DATE_OLDEST -> filteredItems.sortedBy { it.createdAt }
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
    fun onSortOrderSelected(sortOrder: SortOrder) { _sortOrder.value = sortOrder }
    fun onCategorySelected(category: String) { _category.value = category }

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
            categoryRepository.syncCategories()
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
    private val itemDao: ItemDao,
    private val categoryRepository: CategoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, itemDao, categoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}