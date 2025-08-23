package de.lshorizon.whatswhere.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.InventoryApp // Gives access to Room DB and repositories
import de.lshorizon.whatswhere.data.dao.Category
import de.lshorizon.whatswhere.databinding.DialogCategorySelectionBinding
import de.lshorizon.whatswhere.ui.adapter.CategoryAdapter
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModel
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModelFactory
import kotlinx.coroutines.launch

/** Dialog that lets the user pick an existing category or create a new one. */
class CategorySelectionDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCategorySelectionBinding
    // Share AddItemViewModel from the hosting activity using the app's database and repository
    private val viewModel: AddItemViewModel by activityViewModels {
        val app = requireActivity().application as InventoryApp // Obtain InventoryApp for dependencies
        AddItemViewModelFactory(app, app.database.itemDao(), app.categoryRepository)
    }
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.App_AlertDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogCategorySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedCategory = arguments?.getString(ARG_SELECTED_CATEGORY)
        setupRecyclerView(selectedCategory)
        observeCategories()

        binding.editTextNewCategory.setOnEditorActionListener { _, _, _ ->
            val newCategoryName = binding.editTextNewCategory.text.toString().trim()
            if (newCategoryName.isNotEmpty()) {
                viewModel.addCategory(Category(newCategoryName))
                setResult(newCategoryName)
            }
            true
        }
    }

    private fun setupRecyclerView(selectedCategory: String?) {
        categoryAdapter = CategoryAdapter(onCategoryClick = { category ->
            val displayedName = if (category.resourceId != 0) getString(category.resourceId) else category.name
            setResult(displayedName)
        }, selectedCategoryName = selectedCategory)
        binding.recyclerViewCategories.adapter = categoryAdapter
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
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
                categoryAdapter.submitList(finalSortedList)
            }
        }
    }

    private fun setResult(categoryName: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_CATEGORY to categoryName))
        dismiss()
    }

    companion object {
        const val TAG = "CategorySelectionDialogFragment"
        const val REQUEST_KEY = "CategorySelectionRequest"
        const val BUNDLE_KEY_CATEGORY = "SelectedCategory"
        private const val ARG_SELECTED_CATEGORY = "selected_category"

        fun newInstance(selectedCategory: String?): CategorySelectionDialogFragment {
            val args = Bundle()
            args.putString(ARG_SELECTED_CATEGORY, selectedCategory)
            val fragment = CategorySelectionDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
