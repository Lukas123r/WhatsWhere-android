package com.example.whatswhere.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.whatswhere.R
import com.example.whatswhere.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}