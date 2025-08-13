package com.example.whatswhere.ui.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.whatswhere.databinding.DialogLendItemBinding
import java.text.SimpleDateFormat
import java.util.*

class LendItemDialogFragment : DialogFragment() {

    private var selectedReturnDate: Long? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogLendItemBinding.inflate(layoutInflater)
        val calendar = Calendar.getInstance()

        binding.returnDateTextview.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedReturnDate = calendar.timeInMillis
                    val formattedDate = SimpleDateFormat.getDateInstance().format(calendar.time)
                    binding.returnDateTextview.text = "Return by: $formattedDate"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Confirm") { _, _ ->
                val lentToName = binding.lentToEdittext.text.toString().trim()
                if (lentToName.isEmpty() || selectedReturnDate == null) {
                    Toast.makeText(context, "Please fill out all fields", Toast.LENGTH_SHORT).show()
                } else {
                    setFragmentResult(REQUEST_KEY, bundleOf(
                        BUNDLE_KEY_NAME to lentToName,
                        BUNDLE_KEY_DATE to selectedReturnDate!!
                    ))
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    companion object {
        const val TAG = "LendItemDialog"
        const val REQUEST_KEY = "LEND_ITEM_REQUEST"
        const val BUNDLE_KEY_NAME = "LENT_TO_NAME"
        const val BUNDLE_KEY_DATE = "RETURN_DATE"
    }
}