package com.example.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whatswhere.databinding.ListItemTagBinding
import java.util.*

class TagsAdapter(
    private var allTags: List<String>,
    private val selectedTags: MutableSet<String>
) : RecyclerView.Adapter<TagsAdapter.TagViewHolder>() {

    private var filteredTags: List<String> = allTags

    inner class TagViewHolder(val binding: ListItemTagBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ListItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = filteredTags[position]
        holder.binding.tagCheckbox.text = tag
        holder.binding.tagCheckbox.isChecked = selectedTags.contains(tag)

        holder.binding.tagCheckbox.setOnCheckedChangeListener(null) // Wichtig, um Endlos-Schleifen zu vermeiden
        holder.binding.tagCheckbox.isChecked = selectedTags.contains(tag)

        holder.binding.tagCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedTags.add(tag)
            } else {
                selectedTags.remove(tag)
            }
        }
    }

    override fun getItemCount() = filteredTags.size

    fun filter(query: String) {
        filteredTags = if (query.isEmpty()) {
            allTags
        } else {
            allTags.filter {
                it.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
            }
        }
        notifyDataSetChanged()
    }
}