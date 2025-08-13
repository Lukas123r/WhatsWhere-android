package com.example.whatswhere.ui.activity

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
import com.example.whatswhere.InventoryApp
import com.example.whatswhere.R
import com.example.whatswhere.data.Category
import com.example.whatswhere.databinding.ActivityMainBinding
import com.example.whatswhere.ui.adapter.ItemAdapter
import com.example.whatswhere.ui.adapter.ViewType
import com.example.whatswhere.ui.dialog.SortSelectionDialogFragment
import com.example.whatswhere.ui.viewmodel.MainViewModel
import com.example.whatswhere.ui.viewmodel.MainViewModelFactory
import com.example.whatswhere.ui.viewmodel.SortOrder
import com.google.android.material.chip.Chip
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, (application as InventoryApp).database.itemDao())
    }
    private lateinit var adapter: ItemAdapter
    private var accountTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountTextView = binding.toolbar.findViewById(R.id.account_textview)

        setupRecyclerView()
        observeViewModel()
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
        user?.reload()?.addOnFailureListener {
            Toast.makeText(this, "Your session has expired. Please log in again.", Toast.LENGTH_LONG).show()
            Firebase.auth.signOut()
            val intent = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
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
        adapter = ItemAdapter { itemWithTags, itemView ->
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_ITEM_ID, itemWithTags.item.id)
            }
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                itemView,
                "item_image_${itemWithTags.item.id}"
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
                        // KORREKTUR: Übergibt die übersetzten Kategorienamen an den Adapter.
                        val translatedCategories = mainViewModel.allCategories.value.map {
                            it.id to it.getDisplayName(this@MainActivity)
                        }.toMap()
                        adapter.submitData(state.filteredAndSortedList, translatedCategories)

                        val resultsAreEmpty = state.filteredAndSortedList.isEmpty()
                        binding.recyclerView.visibility = if (resultsAreEmpty) View.GONE else View.VISIBLE
                        binding.emptyView.visibility = if (resultsAreEmpty && state.isDatabaseEmpty) View.VISIBLE else View.GONE
                        binding.noResultsView.visibility = if (resultsAreEmpty && !state.isDatabaseEmpty) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    mainViewModel.allCategories.collect { categories ->
                        setupCategoryFilters(categories)
                    }
                }
                launch {
                    mainViewModel.syncMessage.collect { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
                // KORREKTUR: Beobachtet den Ansichtstyp aus dem ViewModel.
                launch {
                    mainViewModel.viewType.collect { viewType ->
                        updateLayoutManager(viewType)
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

    // KORREKTUR: Aktualisiert den LayoutManager basierend auf dem ViewModel-Zustand.
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

    private fun setupCategoryFilters(categories: List<Category>) {
        binding.categoryChipGroup.removeAllViews()

        // "Alle" Chip
        val allChip = layoutInflater.inflate(R.layout.chip_filter, binding.categoryChipGroup, false) as Chip
        allChip.text = getString(R.string.chip_all)
        // KORREKTUR: Wir speichern den Schlüssel als Tag, um ihn später zu identifizieren.
        allChip.tag = "chip_all"
        allChip.id = View.generateViewId()
        binding.categoryChipGroup.addView(allChip)

        // Andere Kategorien
        categories.forEach { category ->
            val chip = layoutInflater.inflate(R.layout.chip_filter, binding.categoryChipGroup, false) as Chip
            // KORREKTUR: Verwendet die Hilfsfunktion, um den übersetzten Namen zu erhalten.
            chip.text = category.getDisplayName(this)
            chip.tag = category.nameKey // Speichert den Schlüssel im Tag
            chip.id = View.generateViewId()
            binding.categoryChipGroup.addView(chip)
        }

        // KORREKTUR: Wählt den richtigen Chip basierend auf dem Schlüssel im ViewModel aus.
        val selectedKey = mainViewModel.selectedCategoryKey.value
        val chipToSelect = binding.categoryChipGroup.findViewWithTag<Chip>(selectedKey)
        if (chipToSelect != null) {
            binding.categoryChipGroup.check(chipToSelect.id)
        } else {
            // Fallback auf "Alle"
            val allChipToSelect = binding.categoryChipGroup.findViewWithTag<Chip>("chip_all")
            allChipToSelect?.let { binding.categoryChipGroup.check(it.id) }
        }


        binding.categoryChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                val allChipToSelect = group.findViewWithTag<Chip>("chip_all")
                allChipToSelect?.let { group.check(it.id) }
                return@setOnCheckedStateChangeListener
            }
            val selectedChip: Chip? = group.findViewById(checkedIds.first())
            selectedChip?.let {
                // KORREKTUR: Übergibt den Schlüssel an das ViewModel.
                mainViewModel.onCategorySelected(it.tag as String)
            }
        }
    }
}