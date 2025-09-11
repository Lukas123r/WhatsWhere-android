package de.lshorizon.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lshorizon.whatswhere.databinding.DropdownItemBinding

class SortOptionAdapter(
    private val options: List<String>,
    private var selectedIndex: Int,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<SortOptionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DropdownItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(options[position], position == selectedIndex)
        holder.itemView.setOnClickListener {
            val newIndex = holder.bindingAdapterPosition
            if (newIndex != RecyclerView.NO_POSITION) {
                val oldIndex = selectedIndex
                selectedIndex = newIndex
                notifyItemChanged(oldIndex)
                notifyItemChanged(newIndex)
                onSelect(newIndex)
            }
        }
    }

    override fun getItemCount(): Int = options.size

    class ViewHolder(private val binding: DropdownItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String, checked: Boolean) {
            binding.text1.text = text
            binding.selectionRadioButton.isChecked = checked
        }
    }
}

