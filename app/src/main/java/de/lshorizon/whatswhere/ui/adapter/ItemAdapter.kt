package de.lshorizon.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.databinding.GridItemBinding
import de.lshorizon.whatswhere.databinding.ListItemBinding

enum class ViewType {
    LIST, GRID
}

class ItemAdapter(private val onItemClicked: (Item, ImageView) -> Unit) :
    ListAdapter<Item, RecyclerView.ViewHolder>(DiffCallback) {

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

    fun submitData(items: List<Item>) {
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
                holder.bind(current)
                holder.itemView.setOnClickListener { onItemClicked(current, holder.binding.itemImage) }
            }
        }
    }

    class ItemListViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val context = binding.root.context

            binding.itemImage.transitionName = "item_image_${item.id}"

            binding.itemName.text = item.name
            binding.itemLocation.text = item.location

            binding.lentStatusTextview.visibility = if (item.isLent) View.VISIBLE else View.GONE

            Glide.with(context).load(item.imagePath).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(binding.itemImage)

            
        }
    }

    class ItemGridViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.itemImage.transitionName = "item_image_${item.id}"
            binding.itemName.text = item.name
            binding.itemLocation.text = item.location
            binding.lentStatusTextview.visibility = if (item.isLent) View.VISIBLE else View.GONE
            Glide.with(binding.root.context).load(item.imagePath).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(binding.itemImage)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem == newItem
            }
        }
    }
}
