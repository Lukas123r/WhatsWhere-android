package com.example.whatswhere.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.whatswhere.R
import com.example.whatswhere.data.ItemWithTags
import com.example.whatswhere.databinding.GridItemBinding
import com.example.whatswhere.databinding.ListItemBinding
import com.google.android.material.chip.Chip

enum class ViewType {
    LIST, GRID
}

class ItemAdapter(private val onItemClicked: (ItemWithTags, ImageView) -> Unit) :
    ListAdapter<ItemWithTags, RecyclerView.ViewHolder>(DiffCallback) {

    private var categoryNameMap: Map<Long, String> = emptyMap()
    private var currentViewType = ViewType.LIST

    fun setViewType(viewType: ViewType) {
        if (currentViewType != viewType) {
            currentViewType = viewType
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return currentViewType.ordinal
    }

    fun submitData(items: List<ItemWithTags>, categoryNames: Map<Long, String>) {
        categoryNameMap = categoryNames
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (ViewType.entries[viewType]) {
            ViewType.LIST -> {
                val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemListViewHolder(binding)
            }
            ViewType.GRID -> {
                val binding = GridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemGridViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val current = getItem(position)
        when (holder) {
            is ItemListViewHolder -> {
                holder.bind(current)
                holder.itemView.setOnClickListener { onItemClicked(current, holder.binding.itemImage) }
            }
            is ItemGridViewHolder -> {
                holder.bind(current, categoryNameMap)
                holder.itemView.setOnClickListener { onItemClicked(current, holder.binding.itemImage) }
            }
        }
    }

    class ItemListViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(itemWithTags: ItemWithTags) {
            val item = itemWithTags.item
            val context = binding.root.context

            binding.itemImage.transitionName = "item_image_${item.id}"

            binding.itemName.text = item.name
            // KORREKTUR: Zeigt jetzt wieder den korrekten Ort des Gegenstands an.
            binding.itemLocation.text = item.location

            binding.lentStatusTextview.visibility = if (item.isLent) View.VISIBLE else View.GONE

            Glide.with(context).load(item.imagePath).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(binding.itemImage)

            binding.tagChipGroup.removeAllViews()
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            val colorPrimary = typedValue.data
            val colorStateList = ColorStateList.valueOf(colorPrimary)
            val strokeWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, context.resources.displayMetrics)

            itemWithTags.tags.take(3).forEach { tag ->
                val chip = Chip(context).apply {
                    text = tag.getDisplayName(context)
                    chipStrokeColor = colorStateList
                    chipStrokeWidth = strokeWidthPx
                    setTextColor(colorStateList)
                    chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                    chipMinHeight = context.resources.getDimension(R.dimen.chip_min_height_small)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                }
                binding.tagChipGroup.addView(chip)
            }
        }
    }

    class ItemGridViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(itemWithTags: ItemWithTags, categoryMap: Map<Long, String>) {
            val item = itemWithTags.item
            binding.itemImage.transitionName = "item_image_${item.id}"
            binding.itemName.text = item.name
            // KORREKTUR: Zeigt den übersetzten Kategorienamen an.
            binding.itemLocation.text = categoryMap[item.categoryId] ?: ""
            binding.lentStatusTextview.visibility = if (item.isLent) View.VISIBLE else View.GONE
            Glide.with(binding.root.context).load(item.imagePath).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(binding.itemImage)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ItemWithTags>() {
            override fun areItemsTheSame(oldItem: ItemWithTags, newItem: ItemWithTags): Boolean {
                return oldItem.item.id == newItem.item.id
            }
            override fun areContentsTheSame(oldItem: ItemWithTags, newItem: ItemWithTags): Boolean {
                return oldItem == newItem
            }
        }
    }
}