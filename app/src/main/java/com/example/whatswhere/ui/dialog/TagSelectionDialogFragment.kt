// Datei: ui/dialog/TagSelectionDialogFragment.kt

package com.example.whatswhere.ui.dialog

import android.util.Log
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
import com.example.whatswhere.ui.adapter.TagSelectionAdapter
import com.example.whatswhere.ui.viewmodel.AddItemViewModel
import com.example.whatswhere.ui.viewmodel.AddItemViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TagSelectionDialogFragment : DialogFragment() {

    private val viewModel: AddItemViewModel by activityViewModels {
        AddItemViewModelFactory((requireActivity().application as InventoryApp).database.itemDao())
    }

    private lateinit var tagAdapter: TagSelectionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_tag_selection, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_tags)
        val newTagEditText = view.findViewById<EditText>(R.id.edit_text_new_tag)
        val confirmButton = view.findViewById<Button>(R.id.btn_confirm_tags)
        val cancelButton = view.findViewById<Button>(R.id.btn_cancel)

        // HIER IST DIE KORREKTUR:
        // 1. Hole die IDs der Tags, die bereits ausgewählt waren.
        val initiallySelectedIds = arguments?.getLongArray(BUNDLE_KEY_IDS)?.toSet() ?: emptySet()

        lifecycleScope.launch {
            val allTagsInitial = viewModel.allTags.first()
            Log.d(TAG, "Initial allTags size: ${allTagsInitial.size}")
            tagAdapter = TagSelectionAdapter(allTagsInitial, initiallySelectedIds)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = tagAdapter

            viewModel.allTags.collect { updatedTags ->
                Log.d(TAG, "Updated allTags size: ${updatedTags.size}")
                tagAdapter.setTags(updatedTags)
            }
        }

        // Listener für das Erstellen neuer Tags
        newTagEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val tagName = v.text.toString().trim()
                Log.d(TAG, "New tag input: '$tagName', actionId: $actionId")
                if (tagName.isNotEmpty()) {
                    viewModel.createNewTag(tagName)
                    v.text = ""
                }
                return@setOnEditorActionListener true
            }
            false
        }

        // Listener für die Buttons
        cancelButton.setOnClickListener { dismiss() }

        confirmButton.setOnClickListener {
            val resultIds = tagAdapter.getSelectedTagIds().toLongArray()
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_IDS to resultIds))
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        // Setze die Breite des Dialogs auf 90% der Bildschirmbreite
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        // Mache den Standard-Rahmen transparent (behebt die KTX-Warnung)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "TagSelectionDialog"
        const val REQUEST_KEY = "TAG_SELECTION_REQUEST"
        const val BUNDLE_KEY_IDS = "SELECTED_TAG_IDS"

        fun newInstance(selectedTagIds: LongArray): TagSelectionDialogFragment {
            return TagSelectionDialogFragment().apply {
                arguments = bundleOf(BUNDLE_KEY_IDS to selectedTagIds)
            }
        }
    }


}