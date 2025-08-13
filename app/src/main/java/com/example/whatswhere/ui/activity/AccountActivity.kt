package com.example.whatswhere.ui.activity

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
import com.example.whatswhere.InventoryApp
import com.example.whatswhere.R
import com.example.whatswhere.data.*
import com.example.whatswhere.databinding.ActivityAccountBinding
import com.example.whatswhere.ui.dialog.ChangePasswordDialogFragment
import com.example.whatswhere.ui.viewmodel.AccountViewModel
import com.example.whatswhere.ui.viewmodel.AccountViewModelFactory
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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
                        fos.write("name,location,description,imagePath,categoryKey,quantity,purchaseDate,price,warrantyExpiration,serialNumber,modelNumber,tagKeys\n".toByteArray())
                        val dao = (application as InventoryApp).database.itemDao()
                        val itemsWithTags = dao.getAllItemsWithTags().first() // Hole alle Items mit ihren Tags
                        val categories = dao.getAllCategories().first().associateBy { cat -> cat.id } // Hole alle Kategorien für Mapping

                        itemsWithTags.forEach { itemWithTags ->
                            val item = itemWithTags.item
                            val categoryKey = categories[item.categoryId]?.nameKey ?: "category_general" // Fallback für Kategorie
                            // KORREKTUR: Verwende tag.nameKey zum Exportieren
                            val tagKeysString = itemWithTags.tags.joinToString(";") { tag -> tag.nameKey }
                            val line = "\"${item.name.replace("\"", "\"\"")}\"," +
                                    "\"${item.location.replace("\"", "\"\"")}\"," +
                                    "\"${(item.description ?: "").replace("\"", "\"\"")}\"," +
                                    "\"${(item.imagePath ?: "").replace("\"", "\"\"")}\"," +
                                    "\"$categoryKey\"," +
                                    "${item.quantity}," +
                                    "${item.purchaseDate ?: ""}," +
                                    "${item.price ?: ""}," +
                                    "${item.warrantyExpiration ?: ""}," +
                                    "\"${(item.serialNumber ?: "").replace("\"", "\"\"")}\"," +
                                    "\"${(item.modelNumber ?: "").replace("\"", "\"\"")}\"," +
                                    "\"$tagKeysString\"\n" // Beachte, dass tagKeysString Anführungszeichen enthalten kann, falls ein Key ; enthält
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
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val header = reader.readLine() // Lese und ignoriere die Header-Zeile
                        if (header == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@AccountActivity, "CSV file is empty or header is missing.", Toast.LENGTH_LONG).show()
                            }
                            return@use
                        }

                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            // Einfache CSV-Analyse, die problematisch sein kann, wenn Felder Kommas enthalten.
                            // Für eine robustere Lösung eine CSV-Parsing-Bibliothek in Betracht ziehen.
                            val tokens = line!!.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                                .map { it.trim().removeSurrounding("\"") } // Entferne äußere Anführungszeichen und trimme

                            if (tokens.size < 12) { // Erwarte mindestens 12 Felder basierend auf dem Export-Header
                                Log.w("AccountActivity", "Skipping malformed CSV line: $line")
                                continue
                            }

                            val categoryKey = tokens[4]
                            var category = dao.getCategoryByNameKey(categoryKey)
                            if (category == null) {
                                val newCatId = dao.insertCategory(Category(nameKey = categoryKey))
                                category = Category(id = newCatId, nameKey = categoryKey)
                            }

                            // Erstelle eine neue ID für das Item, da die CSV keine IDs enthält, oder entscheide, wie IDs gehandhabt werden.
                            // Hier wird für jedes importierte Item eine neue ID in Firestore und lokal generiert.
                            val newDocRefId = FirestoreManager.getItemsCollection().document().id
                            val item = Item(
                                id = newDocRefId, // Neue ID für das importierte Item
                                userId = currentUser.uid,
                                name = tokens[0],
                                location = tokens[1],
                                description = tokens[2].ifEmpty { null },
                                imagePath = tokens[3].ifEmpty { null },
                                categoryId = category.id,
                                quantity = tokens[5].toIntOrNull() ?: 1,
                                purchaseDate = tokens[6].toLongOrNull(),
                                price = tokens[7].toDoubleOrNull(),
                                warrantyExpiration = tokens[8].toLongOrNull(),
                                serialNumber = tokens[9].ifEmpty { null },
                                modelNumber = tokens[10].ifEmpty { null },
                                createdAt = System.currentTimeMillis(),
                                tagsString = tokens[11], // Speichere den rohen String für eine spätere Synchronisation
                                needsSync = true // Markiere als zu synchronisierend mit der Cloud
                            )

                            // Zuerst in Firestore speichern (optional, abhängig von deiner Sync-Strategie)
                            // Wenn du direkt in Firestore speicherst, setze needsSync ggf. auf false
                            // Für dieses Beispiel gehen wir davon aus, dass der SyncManager das später handhabt.
                            // FirestoreManager.saveItem(item)

                            dao.insert(item) // In die lokale Datenbank einfügen
                            val itemId = item.id // Die ID des gerade eingefügten Items

                            val tagKeys = tokens[11].split(";").map { it.trim() }.filter { it.isNotBlank() }
                            val itemTagCrossRefs = mutableListOf<ItemTagCrossRef>()

                            tagKeys.forEach { tagKey ->
                                // KORREKTUR: Verwende getTagByNameKey
                                var tag = dao.getTagByNameKey(tagKey)
                                if (tag == null) {
                                    // KORREKTUR: Verwende nameKey und setze isPredefined = false
                                    val newTag = Tag(nameKey = tagKey, isPredefined = false)
                                    val newTagId = dao.insertTag(newTag)
                                    tag = Tag(id = newTagId, nameKey = tagKey, isPredefined = false)
                                }
                                itemTagCrossRefs.add(ItemTagCrossRef(itemId, tag.id))
                            }
                            if (itemTagCrossRefs.isNotEmpty()) {
                                dao.insertItemTagCrossRefs(itemTagCrossRefs)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_import_success), Toast.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {
                Log.e("AccountActivity", "Error importing data from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountActivity, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

