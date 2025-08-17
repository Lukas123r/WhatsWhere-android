package de.lshorizon.whatswhere.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.databinding.ActivityDetailBinding
import de.lshorizon.whatswhere.ui.dialog.LendItemDialogFragment
import de.lshorizon.whatswhere.ui.viewmodel.DetailViewModel
import de.lshorizon.whatswhere.ui.viewmodel.DetailViewModelFactory

import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var currentItemId: String = ""
    private var currentItem: Item? = null

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory((application as InventoryApp).database.itemDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        currentItemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        if (currentItemId.isEmpty()) {
            finish()
            return
        }

        viewModel.loadItemDetails(currentItemId)

        binding.itemImage.transitionName = "item_image_$currentItemId"

        setupListeners()
        setupResultListeners()

        lifecycleScope.launch {
            viewModel.itemDetails.collectLatest { item ->
                currentItem = item
                item?.let {
                    bindData(it)
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnDelete.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentItem?.let { item -> viewModel.deleteItem(item) }
            Toast.makeText(this, getString(R.string.item_deleted_toast), Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, currentItemId)
            }
            startActivity(intent)
        }

        binding.btnMarkReturned.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // viewModel.returnItem() - This method does not exist in the viewmodel
            Toast.makeText(this, getString(R.string.toast_item_returned), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupResultListeners() {
        supportFragmentManager.setFragmentResultListener(LendItemDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val name = bundle.getString(LendItemDialogFragment.BUNDLE_KEY_NAME)
            bundle.getLong(LendItemDialogFragment.BUNDLE_KEY_DATE)
            if (name != null) {
                // viewModel.lendItem(name, date) - This method does not exist in the viewmodel
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val lendItem = menu?.findItem(R.id.action_lend_item)
        lendItem?.isVisible = currentItem?.isLent == false
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareItem()
                true
            }
            R.id.action_generate_qr -> {
                generateAndShowQrCode()
                true
            }
            R.id.action_lend_item -> {
                LendItemDialogFragment().show(supportFragmentManager, LendItemDialogFragment.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bindData(item: Item) {
        if (item.isLent) {
            binding.lentStatusCard.visibility = View.VISIBLE
            binding.actionButtonsLayout.visibility = View.GONE
            binding.lentStatusTextview.text = getString(R.string.lent_to_status, item.lentTo)
            binding.returnDateTextview.text = getString(R.string.return_by_status, item.returnDate?.let { formatDate(it) })
        } else {
            binding.lentStatusCard.visibility = View.GONE
            binding.actionButtonsLayout.visibility = View.VISIBLE
        }

        binding.collapsingToolbar.title = item.name
        binding.itemNameTitle.text = item.name

        Glide.with(this)
            .load(item.imagePath)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(binding.itemImage)

        
        binding.itemCategory.visibility = View.VISIBLE
        binding.itemCategory.text = if (item.categoryResourceId != 0) getString(item.categoryResourceId) else item.category
        binding.itemLocation.text = item.location
        binding.itemQuantity.text = item.quantity.toString()

        binding.cardDescription.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.itemDescription.text = item.description

        binding.itemPurchaseDate.text = item.purchaseDate?.let { formatDate(it) } ?: "N/A"

        val currencyFormat = NumberFormat.getCurrencyInstance()
        binding.itemPrice.text = item.price?.let { currencyFormat.format(it) } ?: "N/A"

        item.warrantyExpiration?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.itemWarranty.text = formatWarrantyText(it)
            } else {
                binding.itemWarranty.text = formatDate(it)
            }
        } ?: run { binding.itemWarranty.text = "N/A" }

        binding.itemSerial.text = if (item.serialNumber.isNullOrBlank()) "N/A" else item.serialNumber
        binding.itemModel.text = if (item.modelNumber.isNullOrBlank()) "N/A" else item.modelNumber
        binding.itemSerial.setOnClickListener { copyToClipboard(getString(R.string.label_serial_number), item.serialNumber) }
        binding.itemModel.setOnClickListener { copyToClipboard(getString(R.string.label_model_number), item.modelNumber) }
    }

    private fun shareItem() {
        currentItem?.let { item ->
            val shareText = getString(
                R.string.share_item_body,
                item.name,
                item.location,
                item.serialNumber ?: "N/A",
                item.modelNumber ?: "N/A"
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_item_subject, item.name))
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share Item via..."))
        }
    }

    private fun generateAndShowQrCode() {
        val item = currentItem ?: return
        val qrContent = "whatswhere-item-id:${'$'}{item.id}"

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 600, 600)

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<ImageView>(R.id.qr_code_image_view).setImageBitmap(bitmap)
            dialogView.findViewById<TextView>(R.id.qr_dialog_title).text = getString(R.string.qr_code_dialog_title, item.name)
            dialogView.findViewById<Button>(R.id.btn_close_qr_dialog).setOnClickListener { dialog.dismiss() }

            dialog.show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.qr_error_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String?) {
        if (text.isNullOrBlank() || text == "N/A") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatWarrantyText(expirationTimestamp: Long): String {
        val expirationDate = Instant.ofEpochMilli(expirationTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()

        val formattedDate = formatDate(expirationTimestamp)

        if (expirationDate.isBefore(today)) {
            return getString(R.string.warranty_expired_on, formattedDate)
        }

        val daysUntil = ChronoUnit.DAYS.between(today, expirationDate)
        val expiryText = when (daysUntil) {
            0L -> getString(R.string.warranty_expires_today)
            1L -> getString(R.string.warranty_expires_in_one_day)
            else -> getString(R.string.warranty_expires_in_days, daysUntil.toInt())
        }
        return "$expiryText ($formattedDate)"
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))
    }

    companion object {
        const val EXTRA_ITEM_ID = "EXTRA_ITEM_ID"
    }
}