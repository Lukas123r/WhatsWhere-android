package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.databinding.ActivityMainBinding
import de.lshorizon.whatswhere.ui.adapter.ItemAdapter
import de.lshorizon.whatswhere.ui.adapter.ViewType
import de.lshorizon.whatswhere.ui.dialog.SortSelectionDialogFragment
import de.lshorizon.whatswhere.ui.viewmodel.MainViewModel
import de.lshorizon.whatswhere.ui.viewmodel.MainViewModelFactory
import de.lshorizon.whatswhere.ui.viewmodel.SortOrder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, (application as InventoryApp).database.itemDao(), (application as InventoryApp).categoryRepository)
    }
    private lateinit var adapter: ItemAdapter
    private var accountTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountTextView = binding.toolbar.findViewById(R.id.account_textview)

        lifecycleScope.launch {
            val categories = mainViewModel.categories.first()
            if (categories.isEmpty()) {
                (application as InventoryApp).repopulateCategories()
            }
        }

        setupRecyclerView()
        observeViewModel()
        setupCategoryChips()
        setupListeners()
        setupSortResultListener()
        setupSwipeToRefresh()
    }

    override fun onStart() {
        super.onStart()
        checkUserStatus()
        mainViewModel.onRefresh()
    }

    override fun onResume() {
        super.onResume()
        updateAccountIcon()
    }

    private fun checkUserStatus() {
        val user = Firebase.auth.currentUser
        /*user?.reload()?.addOnFailureListener {
            Toast.makeText(this, "Your session has expired. Please log in again.", Toast.LENGTH_LONG).show()
            Firebase.auth.signOut()
            val intent = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }*/
    }

    private fun updateAccountIcon() {
        val user = Firebase.auth.currentUser
        accountTextView?.let { tv ->
            if (user != null && !user.email.isNullOrEmpty()) {
                tv.text = user.email!!.first().uppercase()
                tv.background = ContextCompat.getDrawable(this, R.drawable.account_initials_background)
            } else {
                tv.text = ""
                tv.background = ContextCompat.getDrawable(this, R.drawable.ic_account_circle)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter { item, itemView ->
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_ITEM_ID, item.id)
            }
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                itemView,
                "item_image_${item.id}"
            )
            startActivity(intent, options.toBundle())
        }
        binding.recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.itemListState.collect { state ->
                        adapter.submitList(state.filteredAndSortedList)

                        val resultsAreEmpty = state.filteredAndSortedList.isEmpty()
                        binding.recyclerView.visibility = if (resultsAreEmpty) View.GONE else View.VISIBLE
                        binding.emptyView.visibility = if (resultsAreEmpty && state.isDatabaseEmpty) View.VISIBLE else View.GONE
                        binding.noResultsView.visibility = if (resultsAreEmpty && !state.isDatabaseEmpty) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    mainViewModel.syncMessage.collect { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
                launch {
                    mainViewModel.viewType.collect { viewType ->
                        updateLayoutManager(viewType)
                    }
                }
            }
        }
    }

    private fun setupCategoryChips() {
        lifecycleScope.launch {
            mainViewModel.categories.collect { categories ->
                val chipGroup = binding.categoryChipGroup
                chipGroup.removeAllViews()

                val allCategory = categories.find { it.name.equals("category_all", ignoreCase = true) }
                val otherCategories = categories.filter { !it.name.equals("category_all", ignoreCase = true) }

                val sortedOtherCategories = otherCategories.sortedBy { category ->
                    if (category.resourceId != 0) {
                        getString(category.resourceId).lowercase()
                    } else {
                        category.name.lowercase()
                    }
                }

                val finalSortedList = mutableListOf<de.lshorizon.whatswhere.data.dao.Category>()
                allCategory?.let { finalSortedList.add(it) }
                finalSortedList.addAll(sortedOtherCategories)

                finalSortedList.forEach { category ->
                    val chip = layoutInflater.inflate(R.layout.category_chip, chipGroup, false) as Chip
                    chip.apply {
                        text = if (category.resourceId != 0) getString(category.resourceId) else category.name
                        isCheckable = true
                        id = View.generateViewId()
                        // Check the "All" chip by default
                        if (category.name.equals("category_all", ignoreCase = true)) {
                            isChecked = true
                        }
                    }
                    chipGroup.addView(chip)
                }

                chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
                    if (checkedIds.isNotEmpty()) {
                        val chip = group.findViewById<Chip>(checkedIds.first())
                        val selectedCategory = finalSortedList.find { category ->
                            val displayedName = if (category.resourceId != 0) getString(category.resourceId) else category.name
                            displayedName == chip.text.toString()
                        }
                        selectedCategory?.let { mainViewModel.onCategorySelected(it.name) }
                    } else {
                        // If no chip is selected, default to "All"
                        mainViewModel.onCategorySelected(getString(R.string.category_all))
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.fabAddItem.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, AddItemActivity::class.java))
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                mainViewModel.onSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        binding.toolbar.findViewById<View>(R.id.account_button).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        binding.toolbar.findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.toolbar.findViewById<View>(R.id.sort_button).setOnClickListener {
            val currentSortIndex = mainViewModel.sortOrder.value.ordinal
            SortSelectionDialogFragment.newInstance(currentSortIndex)
                .show(supportFragmentManager, SortSelectionDialogFragment.TAG)
        }

        binding.toolbar.findViewById<ImageButton>(R.id.view_toggle_button).setOnClickListener {
            mainViewModel.toggleViewType()
        }
    }

    private fun setupSortResultListener() {
        supportFragmentManager.setFragmentResultListener(
            SortSelectionDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val selectedIndex = bundle.getInt(SortSelectionDialogFragment.BUNDLE_KEY_INDEX)
            val selectedOrder = SortOrder.entries.getOrElse(selectedIndex) { SortOrder.BY_NAME_ASC }
            mainViewModel.onSortOrderSelected(selectedOrder)
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            mainViewModel.onRefresh(isManual = true)
        }
        lifecycleScope.launch {
            mainViewModel.isRefreshing.collect { isRefreshing ->
                binding.swipeRefreshLayout.isRefreshing = isRefreshing
            }
        }
    }

    private fun updateLayoutManager(viewType: ViewType) {
        val toggleButton = binding.toolbar.findViewById<ImageButton>(R.id.view_toggle_button)
        if (viewType == ViewType.GRID) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
            adapter.setViewType(ViewType.GRID)
            toggleButton.setImageResource(R.drawable.ic_view_list)
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            adapter.setViewType(ViewType.LIST)
            toggleButton.setImageResource(R.drawable.ic_view_grid)
        }
    }
}
