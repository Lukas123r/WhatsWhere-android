package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.*
import de.lshorizon.whatswhere.databinding.ActivityAccountBinding
import de.lshorizon.whatswhere.ui.dialog.ChangePasswordDialogFragment
import de.lshorizon.whatswhere.ui.viewmodel.AccountViewModel
import de.lshorizon.whatswhere.ui.viewmodel.AccountViewModelFactory
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.NumberFormat

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding
    private val viewModel: AccountViewModel by viewModels {
        AccountViewModelFactory((application as InventoryApp).database.itemDao())
    }
    private lateinit var createCsvResult: ActivityResultLauncher<String>
    private lateinit var openCsvResult: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupResultLaunchers()
        setupListeners()
        observeViewModel()

        viewModel.loadUserProfile()
    }

    private fun setupListeners() {
        binding.nameEdittext.doAfterTextChanged { text ->
            viewModel.updateUserName(text.toString())
        }

        binding.logoutButton.setOnClickListener {
            Firebase.auth.signOut()
            val intent = Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        binding.changePasswordButton.setOnClickListener {
            ChangePasswordDialogFragment().show(supportFragmentManager, "ChangePasswordDialog")
        }

        binding.exportDataButton.setOnClickListener {
            val fileName = "whatswhere_export_${System.currentTimeMillis()}.csv"
            createCsvResult.launch(fileName)
        }

        binding.importDataButton.setOnClickListener {
            openCsvResult.launch(arrayOf("text/csv"))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalItemCount.collect { count ->
                    binding.totalItemsTextview.text = count.toString()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalItemValue.collect { value ->
                    val currencyFormat = NumberFormat.getCurrencyInstance()
                    binding.totalValueTextview.text = value?.let { currencyFormat.format(it) } ?: currencyFormat.format(0)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userProfile.collect { user ->
                    if (user != null) {
                        binding.emailTextview.text = user.email
                        if (binding.nameEdittext.text.toString() != user.name) {
                            binding.nameEdittext.setText(user.name)
                        }

                        if (user.photoUrl.isNotEmpty()) {
                            binding.profileInitials.visibility = View.GONE
                            binding.profileImage.visibility = View.VISIBLE
                            Glide.with(this@AccountActivity)
                                .load(user.photoUrl)
                                .placeholder(R.drawable.ic_account_circle)
                                .error(R.drawable.ic_account_circle)
                                .into(binding.profileImage)
                        } else {
                            binding.profileImage.visibility = View.GONE
                            binding.profileInitials.visibility = View.VISIBLE
                            if (user.email.isNotEmpty()) {
                                binding.profileInitials.text = user.email.first().uppercase()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupResultLaunchers() {
        createCsvResult = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            uri?.let { exportDataToUri(it) }
        }

        openCsvResult = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { importDataFromUri(it) }
        }
    }

    private fun exportDataToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        // Header-Zeile für die CSV
                        fos.write("name,location,description,imagePath,quantity,purchaseDate,price,warrantyExpiration,serialNumber,modelNumber\n".toByteArray())
                        val dao = (application as InventoryApp).database.itemDao()
                        val items = dao.getAllItems().first() // Hole alle Items

                        items.forEach { item ->
                            val line = "\"${item.name.replace("\"", "\"\"")}\"," +
                                    "\"${item.location.replace("\"", "\"\"")}\"," +
                                    "\"${(item.description ?: "").replace("\"", "\"\"")}\"," +
                                    "\"${(item.imagePath ?: "").replace("\"", "\"\"")}\"," +
                                    "${item.quantity}," +
                                    "${item.purchaseDate ?: ""}," +
                                    "${item.price ?: ""}," +
                                    "${item.warrantyExpiration ?: ""}," +
                                    "\"${(item.serialNumber ?: "").replace("\"", "\"\"")}\"," +
                                    "\"${(item.modelNumber ?: "").replace("\"", "\"\"")}\"\n"
                            fos.write(line.toByteArray())
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("AccountActivity", "Error exporting data to URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importDataFromUri(uri: Uri) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to import data.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = (application as InventoryApp).database.itemDao()
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        CSVReader(reader).use { csvReader ->
                            csvReader.readNext() // Skip header
                            var tokens: Array<String>?
                            while (csvReader.readNext().also { tokens = it } != null) {
                                if (tokens == null || tokens!!.size < 10) {
                                    Log.w("AccountActivity", "Skipping malformed CSV line: ${tokens?.joinToString(",")}")
                                    continue
                                }

                                val newDocRefId = FirestoreManager.getItemsCollection().document().id
                                val item = Item(
                                    id = newDocRefId,
                                    userId = currentUser.uid,
                                    name = tokens!![0],
                                    location = tokens!![1],
                                    description = tokens!![2].ifEmpty { null },
                                    imagePath = tokens!![3].ifEmpty { null },
                                    quantity = tokens!![4].toIntOrNull() ?: 1,
                                    purchaseDate = tokens!![5].toLongOrNull(),
                                    price = tokens!![6].toDoubleOrNull(),
                                    warrantyExpiration = tokens!![7].toLongOrNull(),
                                    serialNumber = tokens!![8].ifEmpty { null },
                                    modelNumber = tokens!![9].ifEmpty { null },
                                    createdAt = System.currentTimeMillis(),
                                    needsSync = true
                                )

                                dao.insert(item)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_import_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("AccountActivity", "Error importing data from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}