package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.databinding.ActivityDetailBinding
import de.lshorizon.whatswhere.ui.viewmodel.DetailViewModel
import de.lshorizon.whatswhere.ui.viewmodel.DetailViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.combine

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var currentItemId: String = ""
    private var currentItem: Item? = null

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory((application as InventoryApp).database.itemDao(), (application as InventoryApp).categoryRepository)
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
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
                    android.util.Log.d("DetailActivity", "Displaying item: ${it.name}, category: ${it.category}, categoryResourceId: ${it.categoryResourceId}")
                    bindData(it)
                    if (it.categoryResourceId != 0) {
                        binding.itemCategory.text = getString(it.categoryResourceId)
                    } else {
                        binding.itemCategory.text = it.category
                    }
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, currentItemId)
            }
            startActivity(intent)
        }

        binding.btnDelete.setOnClickListener {
            currentItem?.let { item ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete))
                    .setMessage(getString(R.string.delete_confirmation))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        viewModel.deleteItem(item)
                        finish()
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
        }
    }

    private fun setupResultListeners() {
        // No result listeners needed for now
    }

    private fun bindData(item: Item) {
        binding.toolbar.title = item.name
        binding.itemNameTitle.text = item.name
        binding.itemLocation.text = item.location
        binding.itemDescription.text = item.description
        binding.itemQuantity.text = item.quantity.toString()

        if (!item.imagePath.isNullOrEmpty()) {
            Glide.with(this)
                .load(item.imagePath)
                .into(binding.itemImage)
        } else {
            binding.itemImage.setImageResource(R.drawable.ic_image_placeholder)
        }

        item.purchaseDate?.let {
            binding.itemPurchaseDate.text = formatDate(it)
            binding.itemPurchaseDate.visibility = View.VISIBLE
        } ?: run {
            binding.itemPurchaseDate.visibility = View.GONE
        }

        item.warrantyExpiration?.let {
            binding.itemWarranty.text = formatDate(it)
            binding.itemWarranty.visibility = View.VISIBLE
        } ?: run {
            binding.itemWarranty.visibility = View.GONE
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}