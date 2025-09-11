package de.lshorizon.whatswhere.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.ui.adapter.SortOptionAdapter

class SortSelectionDialogFragment : DialogFragment() {

    private var selectedIndex: Int = 0
    private var adapter: SortOptionAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Match the category dialog style (white, rounded)
        setStyle(STYLE_NORMAL, R.style.App_AlertDialogTheme)
        selectedIndex = arguments?.getInt(ARG_CHECKED_ITEM) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_sort_selection, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_view_sort_options)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val options = resources.getStringArray(R.array.sort_options).toList()
        adapter = SortOptionAdapter(options, selectedIndex) { index -> selectedIndex = index }
        recycler.adapter = adapter

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            ?.setOnClickListener { dismiss() }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_apply)
            ?.setOnClickListener {
                setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_INDEX to selectedIndex))
                dismiss()
            }

        return view
    }

    override fun onStart() {
        super.onStart()
        // Align with category dialog: fixed width to avoid any relayout jump
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
