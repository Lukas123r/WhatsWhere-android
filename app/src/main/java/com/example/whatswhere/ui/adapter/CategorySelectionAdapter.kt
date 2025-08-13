package com.example.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import com.example.whatswhere.R
import com.example.whatswhere.data.Category

class CategorySelectionAdapter(
    private var allCategories: List<Category>,
    private var selectedCategoryId: Long
) : RecyclerView.Adapter<CategorySelectionAdapter.CategoryViewHolder>() {

    private var lastChecked: RadioButton? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_selection, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(allCategories[position])
    }

    override fun getItemCount(): Int = allCategories.size

    fun getSelectedCategoryId(): Long = selectedCategoryId

    fun setCategories(categories: List<Category>) {
        allCategories = categories
        val newPosition = allCategories.indexOfFirst { it.id == selectedCategoryId }
        notifyDataSetChanged()
        if (newPosition == -1) {
            lastChecked = null
        }
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button_category)

        fun bind(category: Category) {
            // KORREKTUR: Verwendet die Hilfsfunktion, um den übersetzten Namen zu erhalten.
            radioButton.text = category.getDisplayName(itemView.context)
            radioButton.isChecked = (category.id == selectedCategoryId)
            if (radioButton.isChecked) {
                lastChecked = radioButton
            }

            radioButton.setOnClickListener {
                lastChecked?.isChecked = false
                selectedCategoryId = category.id
                radioButton.isChecked = true
                lastChecked = radioButton
            }
        }
    }
}