package com.example.whatswhere.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.example.whatswhere.R
import com.example.whatswhere.databinding.DialogChangePasswordBinding

class ChangePasswordDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogChangePasswordBinding.inflate(layoutInflater)
        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.dialog_change_password_title)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_button_confirm) { _, _ ->
                val currentPassword = binding.currentPasswordEdittext.text.toString()
                val newPassword = binding.newPasswordEdittext.text.toString()
                val confirmPassword = binding.confirmPasswordEdittext.text.toString()

                val savedPassword = sharedPrefs.getString("user_password", "")

                if (currentPassword != savedPassword) {
                    Toast.makeText(context, getString(R.string.toast_password_incorrect), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPassword.length < 6) {
                    Toast.makeText(context, getString(R.string.toast_password_too_short), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPassword != confirmPassword) {
                    Toast.makeText(context, getString(R.string.toast_passwords_no_match), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sharedPrefs.edit {
                    putString("user_password", newPassword)
                }
                Toast.makeText(context, getString(R.string.toast_password_changed), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_button_cancel, null)
            .create()
    }
}