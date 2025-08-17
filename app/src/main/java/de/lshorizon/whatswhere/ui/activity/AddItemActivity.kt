package de.lshorizon.whatswhere.ui.activity

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.databinding.ActivityAddItemBinding
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModel
import de.lshorizon.whatswhere.ui.viewmodel.AddItemViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

import de.lshorizon.whatswhere.data.dao.Category
import de.lshorizon.whatswhere.ui.dialog.CategorySelectionDialogFragment
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddItemBinding
    private val viewModel: AddItemViewModel by viewModels {
        AddItemViewModelFactory(application, (application as InventoryApp).database.itemDao(), (application as InventoryApp).categoryRepository)
    }

    private var currentItemId: String = ""
    private var localImageUri: Uri? = null
    private var finalImageUrl: String? = null

    private var selectedPurchaseDate: Long? = null
    private var selectedWarrantyDate: Long? = null
    private var existingCreatedAt: Long = 0L
    private var existingUserId: String? = null // Für den Fall, dass ein Item bearbeitet wird

    private lateinit var takeImageResult: ActivityResultLauncher<Uri>
    private lateinit var selectImageResult: ActivityResultLauncher<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentItemId = intent.getStringExtra(DetailActivity.EXTRA_ITEM_ID) ?: ""

        setupResultLaunchers()
        setupDatePickers()
        setupCategorySelector()

        if (currentItemId.isNotEmpty()) {
            binding.toolbar.title = getString(R.string.edit_item_title)
            binding.btnSave.text = getString(R.string.update_item_button)
            viewModel.loadItem(currentItemId)
            observeItemToEdit()
        } else {
            // Beim Erstellen eines neuen Items die UserID des aktuellen Users setzen
            existingUserId = Firebase.auth.currentUser?.uid
        }


        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveItem() }
        binding.imageContainer.setOnClickListener { showPhotoOptions() }
    }

    private fun observeItemToEdit() {
        lifecycleScope.launch {
            viewModel.itemToEdit.collect { item ->
                item?.let {
                    populateUiForEdit(it)
                    viewModel.clearItemToEdit() // Optional: ItemToEdit zurücksetzen, nachdem es geladen wurde
                }
            }
        }
    }

    private fun populateUiForEdit(item: Item) {
        binding.editTextName.setText(item.name)
        binding.editTextLocation.setText(item.location)
        binding.editTextCategory.setText(item.category)
        binding.editTextDescription.setText(item.description)
        binding.editTextQuantity.setText(item.quantity.toString())
        existingCreatedAt = item.createdAt
        existingUserId = item.userId // Wichtig: UserId für das zu bearbeitende Item übernehmen

        item.imagePath?.let {
            if (it.isNotEmpty()) {
                finalImageUrl = it
                displayImage(it.toUri())
            }
        }

        item.purchaseDate?.let {
            selectedPurchaseDate = it
            binding.editTextPurchaseDate.setText(formatDate(it))
        }

        item.warrantyExpiration?.let {
            selectedWarrantyDate = it
            binding.editTextWarrantyDate.setText(formatDate(it))
        }
    }

    private fun setupResultLaunchers() {
        selectImageResult = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                localImageUri = it
                displayImage(it)
            }
        }
        takeImageResult = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                localImageUri?.let { displayImage(it) }
            }
        }
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchCameraIntent()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
    }

    private fun uploadImageToFirebase(uri: Uri, onComplete: (String?) -> Unit) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            showLoading(false)
            onComplete(null)
            return
        }

        showLoading(true)

        val imageBytes = try {
            contentResolver.openInputStream(uri)?.readBytes()
        } catch (e: Exception) {
            Log.e("AddItemActivity", "Failed to read image file", e)
            Toast.makeText(this, "Failed to read image file.", Toast.LENGTH_SHORT).show()
            showLoading(false)
            onComplete(null)
            return
        }

        if (imageBytes == null) {
            Toast.makeText(this, "Could not process image.", Toast.LENGTH_SHORT).show()
            showLoading(false)
            onComplete(null)
            return
        }

        val storageRef = Firebase.storage.reference
        // User-spezifischer Pfad
        val imageRef = storageRef.child("images/${user.uid}/${System.currentTimeMillis()}.jpg")

        imageRef.putBytes(imageBytes)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onComplete(downloadUri.toString())
                }.addOnFailureListener { e ->
                    Log.e("AddItemActivity", "Failed to get download URL", e)
                    Toast.makeText(this, "Failed to get download URL.", Toast.LENGTH_SHORT).show()
                    onComplete(null) // Wichtig: onComplete auch bei Fehler aufrufen
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddItemActivity", "Upload failed", e)
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(null) // Wichtig: onComplete auch bei Fehler aufrufen
            }
            .addOnCompleteListener { // Wird immer aufgerufen, egal ob Erfolg oder Fehler
                showLoading(false) // Ladeanzeige ausblenden
            }
    }

    private fun saveItem() {
        if (localImageUri != null && (finalImageUrl == null || localImageUri.toString() != finalImageUrl)) {
            // Nur hochladen, wenn ein neues lokales Bild ausgewählt wurde ODER
            // wenn es ein lokales Bild gibt und noch keine finale URL (z.B. neues Item)
            uploadImageToFirebase(localImageUri!!) { uploadedImageUrl ->
                // showLoading(false) ist jetzt im onCompleteListener von uploadImageToFirebase
                if (uploadedImageUrl != null) {
                    finalImageUrl = uploadedImageUrl
                    saveItemToDatabase()
                } else {
                    // Zeige Fehler nur, wenn der Upload tatsächlich versucht und fehlgeschlagen ist
                    if (Firebase.auth.currentUser != null) { // Prüfe ob User noch eingeloggt ist, bevor Toast gezeigt wird
                        Toast.makeText(this, "Image upload failed. Please try again or save without image.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // Kein neues Bild zum Hochladen oder Bild ist bereits das von Firestore
            saveItemToDatabase()
        }
    }

    private fun saveItemToDatabase() {
        val name = binding.editTextName.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val currentUserId = Firebase.auth.currentUser?.uid

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not identified. Please re-login.", Toast.LENGTH_LONG).show()
            // Optional: Zur Login-Activity navigieren
            return
        }

        if (name.isEmpty() || location.isEmpty() || category.isEmpty()) {
            Toast.makeText(this, getString(R.string.name_and_location_category_cannot_be_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val itemToSave = Item(
            id = if (currentItemId.isNotEmpty()) currentItemId else UUID.randomUUID().toString(), // Generiere neue ID für neues Item
            userId = if (currentItemId.isNotEmpty()) existingUserId ?: currentUserId else currentUserId, // Verwende bestehende oder aktuelle UserID
            name = name,
            location = location,
            category = category,
            description = binding.editTextDescription.text.toString().trim(),
            imagePath = finalImageUrl,
            quantity = binding.editTextQuantity.text.toString().toIntOrNull() ?: 1,
            purchaseDate = selectedPurchaseDate,
            price = null, // Du hast hier kein Feld für den Preis, ggf. hinzufügen oder entfernen
            warrantyExpiration = selectedWarrantyDate,
            serialNumber = null, // Du hast hier kein Feld, ggf. hinzufügen oder entfernen
            modelNumber = null,  // Du hast hier kein Feld, ggf. hinzufügen oder entfernen
            createdAt = if (currentItemId.isNotEmpty()) existingCreatedAt else System.currentTimeMillis(),
            needsSync = true // Immer als 'needsSync' markieren, SyncManager kümmert sich darum
        )
        viewModel.saveOrUpdateItem(itemToSave, category)
        finish()
    }

    private fun launchCameraIntent() {
        val tmpFile = try {
            File.createTempFile("tmp_image_file_", ".png", cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
        } catch (e: Exception) {
            Log.e("AddItemActivity", "Error creating temp file for camera", e)
            Toast.makeText(this, "Could not start camera.", Toast.LENGTH_SHORT).show()
            return
        }

        val tempUri = FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.provider", tmpFile)
        localImageUri = tempUri // Speichere die Uri des temporären Files
        takeImageResult.launch(tempUri)
    }

    private fun displayImage(uri: Uri) {
        binding.addPhotoPlaceholder.visibility = View.GONE
        binding.itemImagePreview.visibility = View.VISIBLE
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .placeholder(R.drawable.ic_image_placeholder) // Optional: Platzhalter
            .error(R.drawable.ic_broken_image) // Optional: Fehlerbild
            .into(binding.itemImagePreview)
    }

    private fun selectImage() {
        selectImageResult.launch("image/*")
    }

    private fun showPhotoOptions() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_photo_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<MaterialButton>(R.id.btn_take_photo).setOnClickListener {
            handleTakePhotoClick()
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_select_gallery).setOnClickListener {
            selectImage()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun handleTakePhotoClick() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCameraIntent()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Hier könntest du einen Dialog anzeigen, der erklärt, warum die Berechtigung benötigt wird.
                Toast.makeText(this, "Camera permission is needed to take photos.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA) // Erneut anfordern
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupDatePickers() {
        binding.editTextPurchaseDate.setOnClickListener {
            showDatePicker(selectedPurchaseDate) { timestamp ->
                selectedPurchaseDate = timestamp
                binding.editTextPurchaseDate.setText(formatDate(timestamp))
            }
        }
        binding.editTextWarrantyDate.setOnClickListener {
            showDatePicker(selectedWarrantyDate) { timestamp ->
                selectedWarrantyDate = timestamp
                binding.editTextWarrantyDate.setText(formatDate(timestamp))
            }
        }
    }

    private fun showDatePicker(initialTimestamp: Long?, onDateSelected: (timestamp: Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (initialTimestamp != null && initialTimestamp > 0) { // Prüfe ob Timestamp valide ist
            calendar.timeInMillis = initialTimestamp
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                    // Setze Uhrzeit auf Mitternacht, um Konsistenz zu gewährleisten
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateSelected(selectedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    private fun setupCategorySelector() {
        binding.editTextCategory.setOnClickListener {
            val selectedCategory = binding.editTextCategory.text.toString()
            CategorySelectionDialogFragment.newInstance(selectedCategory)
                .show(supportFragmentManager, CategorySelectionDialogFragment.TAG)
        }

        supportFragmentManager.setFragmentResultListener(
            CategorySelectionDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val selectedCategory = bundle.getString(CategorySelectionDialogFragment.BUNDLE_KEY_CATEGORY)
            binding.editTextCategory.setText(selectedCategory)
        }
    }

    
}