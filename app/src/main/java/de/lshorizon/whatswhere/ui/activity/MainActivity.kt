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
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, (application as InventoryApp).database.itemDao(), (application as InventoryApp).categoryRepository)
    }
    private lateinit var adapter: ItemAdapter
    private var accountTextView: TextView? = null
    private var selectedCategoryText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstLaunch = sharedPreferences.getBoolean("is_first_launch", true)

        if (isFirstLaunch) {
            val deviceLanguage = Locale.getDefault().language
            val supportedLanguages = listOf("en", "de", "it", "fr", "es")
            val languageToSet = if (supportedLanguages.contains(deviceLanguage)) {
                deviceLanguage
            } else {
                "en" // Default to English if device language is not supported
            }
            setAppLanguage(this, languageToSet)

            sharedPreferences.edit().putBoolean("is_first_launch", false).apply()
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountTextView = binding.toolbar.findViewById(R.id.account_textview)
        selectedCategoryText = getString(R.string.category_all)

        

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

                categories.forEach { category ->
                    val chip = layoutInflater.inflate(R.layout.category_chip, chipGroup, false) as Chip
                    chip.apply {
                        text = if (category.resourceId != 0) getString(category.resourceId) else category.name
                        isCheckable = true
                        id = View.generateViewId()
                        // Reflect the current selection (defaults to "All")
                        isChecked = text.toString() == selectedCategoryText
                    }
                    chipGroup.addView(chip)
                }

                chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
                    if (checkedIds.isNotEmpty()) {
                        val chip = group.findViewById<Chip>(checkedIds.first())
                        selectedCategoryText = chip.text.toString()
                        mainViewModel.onCategorySelected(selectedCategoryText)
                    } else {
                        // If user taps an already-selected chip, ChipGroup clears selection.
                        // We want to default back to "All" and visually reselect it.
                        val allText = getString(R.string.category_all)
                        var allChip: Chip? = null
                        for (i in 0 until group.childCount) {
                            val child = group.getChildAt(i)
                            if (child is Chip) {
                                if (child.text?.toString() == allText || child.text?.toString()?.lowercase() == "all") {
                                    allChip = child
                                    break
                                }
                            }
                        }
                        allChip?.isChecked = true
                        selectedCategoryText = allText
                        mainViewModel.onCategorySelected(allText)
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
            // Prevent opening multiple dialogs on rapid taps
            if (supportFragmentManager.findFragmentByTag(SortSelectionDialogFragment.TAG) == null) {
                SortSelectionDialogFragment.newInstance(currentSortIndex)
                    .show(supportFragmentManager, SortSelectionDialogFragment.TAG)
            }
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

    private fun setAppLanguage(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
