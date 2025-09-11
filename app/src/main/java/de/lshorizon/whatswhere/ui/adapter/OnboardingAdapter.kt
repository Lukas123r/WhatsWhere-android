package de.lshorizon.whatswhere.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lshorizon.whatswhere.databinding.ItemOnboardingPageBinding
import de.lshorizon.whatswhere.ui.activity.OnboardingPage

class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<OnboardingAdapter.VH>() {

    class VH(val binding: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = pages[position]
        holder.binding.image.setImageResource(p.imageRes)
        holder.binding.title.setText(p.titleRes)
        holder.binding.description.setText(p.descRes)
    }
}
