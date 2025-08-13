package com.example.whatswhere.ui.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatswhere.InventoryApp
import com.example.whatswhere.R
import com.example.whatswhere.ui.adapter.CategorySelectionAdapter
import com.example.whatswhere.ui.viewmodel.AddItemViewModel
import com.example.whatswhere.ui.viewmodel.AddItemViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategorySelectionDialogFragment : DialogFragment() {
    private val viewModel: AddItemViewModel by activityViewModels {
        AddItemViewModelFactory((requireActivity().application as InventoryApp).database.itemDao())
    }
    private lateinit var categoryAdapter: CategorySelectionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_category_selection, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_categories)
        val newCategoryEditText = view.findViewById<EditText>(R.id.edit_text_new_category)
        val confirmButton = view.findViewById<Button>(R.id.btn_confirm_category)
        val cancelButton = view.findViewById<Button>(R.id.btn_cancel)

        val initiallySelectedId = arguments?.getLong(BUNDLE_KEY_ID) ?: -1

        lifecycleScope.launch {
            val allCategories = viewModel.allCategories.first()
            categoryAdapter = CategorySelectionAdapter(allCategories, initiallySelectedId)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = categoryAdapter
        }

        newCategoryEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val categoryName = v.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    viewModel.createNewCategory(categoryName)
                    v.text = ""
                    // KORREKTUR: Beobachtet die Live-Änderungen der Kategorien
                    lifecycleScope.launch {
                        viewModel.allCategories.collect { updatedCategories ->
                            categoryAdapter.setCategories(updatedCategories)
                        }
                    }
                }
                return@setOnEditorActionListener true
            }
            false
        }

        cancelButton.setOnClickListener { dismiss() }

        confirmButton.setOnClickListener {
            val resultId = categoryAdapter.getSelectedCategoryId()
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_ID to resultId))
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "CategorySelectionDialog"
        const val REQUEST_KEY = "CATEGORY_SELECTION_REQUEST"
        const val BUNDLE_KEY_ID = "SELECTED_CATEGORY_ID"
        fun newInstance(selectedCategoryId: Long): CategorySelectionDialogFragment {
            return CategorySelectionDialogFragment().apply {
                arguments = bundleOf(BUNDLE_KEY_ID to selectedCategoryId)
            }
        }
    }
}