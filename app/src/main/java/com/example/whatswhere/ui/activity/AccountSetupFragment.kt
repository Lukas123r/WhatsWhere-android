package com.example.whatswhere.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.whatswhere.R
import com.example.whatswhere.databinding.FragmentAccountSetupBinding

class AccountSetupFragment : Fragment() {
    private var _binding: FragmentAccountSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun isDataValid(): Boolean {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null

        val email = binding.emailEdittext.text.toString().trim()
        val password = binding.passwordEdittext.text.toString().trim()
        var isValid = true

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        // HIER DIE NEUE VALIDIERUNG
        if (password.length < 6 || !password.contains(Regex("[0-9]")) || !password.contains(Regex("[^a-zA-Z0-9]"))) {
            binding.passwordLayout.error = getString(R.string.error_password_length) // Der Text passt immer noch gut
            isValid = false
        }

        return isValid
    }

    fun getEmail(): String = binding.emailEdittext.text.toString().trim()

    fun getPassword(): String = binding.passwordEdittext.text.toString().trim()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}