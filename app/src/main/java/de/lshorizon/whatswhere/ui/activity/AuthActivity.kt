package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.FirestoreManager
import de.lshorizon.whatswhere.databinding.ActivityAuthBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        if (auth.currentUser != null) {
            navigateToMainApp()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        setupListeners()
        updateUiForAuthMode()
    }

    private fun updateUiForAuthMode() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        if (isLoginMode) {
            binding.titleTextview.text = "Login"
            binding.subtitleTextview.text = "Sign in to continue"
            binding.authActionButton.text = "Login"
            binding.switchAuthModeText.text = "No account yet? Register"
            binding.passwordLayout.helperText = null
        } else {
            binding.titleTextview.text = "Register"
            binding.subtitleTextview.text = "Create an account to get started"
            binding.authActionButton.text = "Register"
            binding.switchAuthModeText.text = "Already have an account? Login"
            binding.passwordLayout.helperText = getString(R.string.password_requirements)
        }
    }

    private fun setupListeners() {
        binding.authActionButton.setOnClickListener {
            if (isLoginMode) {
                handleEmailSignIn()
            } else {
                handleEmailRegistration()
            }
        }
        binding.switchAuthModeText.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUiForAuthMode()
        }
        binding.googleSigninButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@AuthActivity, request)
                val credential = result.credential
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: GetCredentialException) {
                Toast.makeText(this@AuthActivity, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleEmailSignIn() {
        val email = binding.emailEdittext.text.toString().trim()
        val password = binding.passwordEdittext.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToMainApp()
                } else {
                    Toast.makeText(baseContext, "Login failed. Please check your credentials.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleEmailRegistration() {
        if (!validateForm()) return

        val email = binding.emailEdittext.text.toString().trim()
        val password = binding.passwordEdittext.text.toString().trim()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val newUser = task.result.user
                    if (newUser != null) {
                        lifecycleScope.launch {
                            try {
                                FirestoreManager.createUserProfileDocument(newUser)
                                navigateToMainApp()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@AuthActivity,
                                    "Account created, but failed to save profile: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(baseContext, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val email = binding.emailEdittext.text.toString().trim()
        val password = binding.passwordEdittext.text.toString().trim()

        binding.emailLayout.error = null
        binding.passwordLayout.error = null

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        if (password.length < 6 || !password.contains(Regex("[0-9]")) || !password.contains(Regex("[^a-zA-Z0-9]"))) {
            binding.passwordLayout.error = getString(R.string.password_requirements)
            isValid = false
        }

        return isValid
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val isNewUser = task.result.additionalUserInfo?.isNewUser ?: false
                    val user = task.result.user

                    if (isNewUser && user != null) {
                        lifecycleScope.launch {
                            try {
                                FirestoreManager.createUserProfileDocument(user)
                                navigateToMainApp()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@AuthActivity,
                                    "Account created, but failed to save profile: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        navigateToMainApp()
                    }
                } else {
                    Toast.makeText(this, "Firebase authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
