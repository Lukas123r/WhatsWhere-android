package de.lshorizon.whatswhere.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.CategoryRepository
import de.lshorizon.whatswhere.data.dao.Category
import de.lshorizon.whatswhere.databinding.DialogCategorySelectionBinding
import de.lshorizon.whatswhere.ui.adapter.CategoryAdapter
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModel
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModelFactory
import kotlinx.coroutines.launch

class CategorySelectionDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCategorySelectionBinding
    private val viewModel: AddItemViewModel by viewModels {
        val application = requireActivity().application as de.lshorizon.whatswhere.InventoryApp
        AddItemViewModelFactory(
            requireActivity().application,
            application.database.itemDao(),
            CategoryRepository(application.database.categoryDao())
        )
    }
    private lateinit var categoryAdapter: CategoryAdapter
    private var initialSelectedCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.App_AlertDialogTheme)
        initialSelectedCategory = arguments?.getString(ARG_SELECTED_CATEGORY)
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

        setupRecyclerView()
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

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter({
            val localizedCategoryName = if (it.resourceId != 0) {
                requireContext().getString(it.resourceId)
            } else {
                it.name
            }
            setResult(localizedCategoryName)
        }, initialSelectedCategory)
        binding.recyclerViewCategories.adapter = categoryAdapter
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                val allCategory = Category("all", R.string.category_all)
                val sortedCategories = categories
                    .filter { category ->
                        val isAllCategoryByName = category.name.lowercase() == "all"
                        val isAllCategoryByResourceId = category.resourceId == R.string.category_all
                        !(isAllCategoryByName || isAllCategoryByResourceId)
                    }
                    .sortedBy { 
                        if (it.resourceId != 0) {
                            requireContext().getString(it.resourceId).lowercase()
                        } else {
                            it.name.lowercase()
                        }
                    }
                val finalCategories = mutableListOf<Category>()
                finalCategories.add(allCategory)
                finalCategories.addAll(sortedCategories)
                categoryAdapter.submitList(finalCategories)
                // Update the selected category in the adapter after the list is submitted
                categoryAdapter.setSelectedCategory(initialSelectedCategory)
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
