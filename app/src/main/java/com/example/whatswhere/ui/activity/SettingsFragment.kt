package com.example.whatswhere.ui.activity

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.whatswhere.R
import com.example.whatswhere.ui.util.CacheManager
import com.example.whatswhere.ui.util.LocaleHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var clearCachePreference: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        clearCachePreference = findPreference("clear_cache")

        clearCachePreference?.setOnPreferenceClickListener {
            showClearCacheConfirmationDialog()
            true
        }
        updateAppVersionSummary()
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateCacheSummary()
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "theme" -> {
                val themeValue = sharedPreferences?.getString(key, "system")
                applyTheme(themeValue)
            }
            "language" -> {
                val languageCode = sharedPreferences?.getString(key, "en") ?: "en"
                LocaleHelper.setLocale(requireContext(), languageCode)
                // KORREKTUR: Eigene Bestätigungsmeldung anzeigen
                Toast.makeText(requireContext(), getString(R.string.toast_language_changed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyTheme(themeValue: String?) {
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun updateCacheSummary() {
        val cacheSize = CacheManager.getCacheSize(requireContext())
        clearCachePreference?.summary = getString(R.string.settings_cache_summary_dynamic, cacheSize)
    }

    private fun showClearCacheConfirmationDialog() {
        val cacheSizeString = CacheManager.getCacheSize(requireContext())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_clear_cache_title))
            .setMessage(getString(R.string.dialog_clear_cache_message, cacheSizeString))
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .setPositiveButton(getString(R.string.dialog_button_clear)) { _, _ ->
                CacheManager.clearCache(requireContext()) {
                    updateCacheSummary()
                    Toast.makeText(requireContext(), getString(R.string.toast_cache_cleared_success), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun updateAppVersionSummary() {
        try {
            val pInfo = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
            val version = pInfo.versionName
            findPreference<Preference>("app_version")?.summary = version
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}