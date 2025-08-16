package com.example.whatswhere.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.whatswhere.R
import com.example.whatswhere.data.dao.Category
import com.example.whatswhere.databinding.DialogCategorySelectionBinding
import com.example.whatswhere.ui.adapter.CategoryAdapter
import com.example.whatswhere.ui.viewmodel.AddItemViewModel
import com.example.whatswhere.ui.viewmodel.AddItemViewModelFactory
import kotlinx.coroutines.launch

class CategorySelectionDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCategorySelectionBinding
    private val viewModel: AddItemViewModel by viewModels {
        AddItemViewModelFactory(
            requireActivity().application,
            (requireActivity().application as com.example.whatswhere.InventoryApp).database.itemDao(),
            (requireActivity().application as com.example.whatswhere.InventoryApp).database.categoryDao()
        )
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
        val selectedCategory = arguments?.getString(ARG_SELECTED_CATEGORY)
        categoryAdapter = CategoryAdapter({
            setResult(it.name)
        }, selectedCategory)
        binding.recyclerViewCategories.adapter = categoryAdapter
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                val filteredCategories = categories.filter { it.name.lowercase() != "all" }
                categoryAdapter.submitList(filteredCategories)
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