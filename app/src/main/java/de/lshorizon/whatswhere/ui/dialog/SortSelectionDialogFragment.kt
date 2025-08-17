package de.lshorizon.whatswhere.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.lshorizon.whatswhere.R

class SortSelectionDialogFragment : DialogFragment() {

    private var selectedIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedIndex = arguments?.getInt(ARG_CHECKED_ITEM) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val sortOptions = resources.getStringArray(R.array.sort_options)

        return AlertDialog.Builder(requireContext())
            // KORREKTUR: Titel aus String-Ressource laden
            .setTitle(getString(R.string.dialog_title_sort_by))
            .setSingleChoiceItems(sortOptions, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(getString(R.string.dialog_button_apply)) { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_INDEX to selectedIndex))
                dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .create()
    }

    companion object {
        const val TAG = "SortSelectionDialog"
        const val REQUEST_KEY = "SORT_SELECTION_REQUEST"
        const val BUNDLE_KEY_INDEX = "SORT_SELECTION_INDEX"
        private const val ARG_CHECKED_ITEM = "ARG_CHECKED_ITEM"

        fun newInstance(checkedItemIndex: Int): SortSelectionDialogFragment {
            return SortSelectionDialogFragment().apply {
                arguments = bundleOf(ARG_CHECKED_ITEM to checkedItemIndex)
            }
        }
    }
}