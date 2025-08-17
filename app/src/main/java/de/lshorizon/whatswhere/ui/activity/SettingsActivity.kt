package de.lshorizon.whatswhere.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.databinding.ActivitySettingsBinding

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