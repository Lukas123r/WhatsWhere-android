package com.example.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.whatswhere.R
import com.example.whatswhere.data.Tag

class TagSelectionAdapter(
    private var allTags: List<Tag>,
    initiallySelectedIds: Set<Long>
) : RecyclerView.Adapter<TagSelectionAdapter.TagViewHolder>() {

    private val selectedTagIds = initiallySelectedIds.toMutableSet()

    fun setTags(tags: List<Tag>) {
        allTags = tags
        notifyDataSetChanged()
    }

    fun getSelectedTagIds(): Set<Long> = selectedTagIds

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_selection, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(allTags[position])
    }

    override fun getItemCount(): Int = allTags.size

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_tag)

        fun bind(tag: Tag) {
            // KORREKTUR: Verwendet die Hilfsfunktion für den übersetzten Namen.
            checkBox.text = tag.getDisplayName(itemView.context)
            checkBox.isChecked = selectedTagIds.contains(tag.id)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTagIds.add(tag.id)
                } else {
                    selectedTagIds.remove(tag.id)
                }
            }
        }
    }
}