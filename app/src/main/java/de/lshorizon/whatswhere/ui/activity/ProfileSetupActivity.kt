package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.FirestoreManager
import de.lshorizon.whatswhere.databinding.ActivityProfileSetupBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val name = binding.nameEdittext.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val user = Firebase.auth.currentUser
            if (user == null) {
                Toast.makeText(this, getString(R.string.toast_sync_error_not_logged_in), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    FirestoreManager.updateUserName(user.uid, name)
                    startActivity(Intent(this@ProfileSetupActivity, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@ProfileSetupActivity, getString(R.string.toast_sync_error_generic, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    binding.progress.visibility = View.GONE
                }
            }
        }
    }
}

