package de.lshorizon.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.lshorizon.whatswhere.data.dao.Category
import de.lshorizon.whatswhere.databinding.DropdownItemBinding

class CategoryAdapter(private val onCategoryClick: (Category) -> Unit, private val selectedCategoryName: String?) :
    ListAdapter<Category, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = DropdownItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        holder.bind(category, selectedCategoryName)
        holder.itemView.setOnClickListener {
            onCategoryClick(category)
        }
    }

    class CategoryViewHolder(private val binding: DropdownItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category, selectedCategoryName: String?) {
            binding.text1.text = if (category.resourceId != 0) binding.text1.context.getString(category.resourceId) else category.name
            val displayedCategoryName = if (category.resourceId != 0) binding.text1.context.getString(category.resourceId) else category.name
            binding.selectionRadioButton.isChecked = displayedCategoryName == selectedCategoryName
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem == newItem
        }
    }
}
