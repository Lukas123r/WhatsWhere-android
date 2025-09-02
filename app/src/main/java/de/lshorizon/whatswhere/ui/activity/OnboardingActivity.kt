package de.lshorizon.whatswhere.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import de.lshorizon.whatswhere.databinding.ActivityOnboardingBinding
import de.lshorizon.whatswhere.ui.adapter.OnboardingAdapter

data class OnboardingPage(
    val imageRes: Int,
    val titleRes: Int,
    val descRes: Int
)

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter
    private val pages = mutableListOf<OnboardingPage>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildPages()
        setupRecycler()
        setupDots()
        setupButtons()
        updateUi()
    }

    private fun buildPages() {
        pages.add(
            OnboardingPage(
                imageRes = de.lshorizon.whatswhere.R.drawable.ic_empty_box,
                titleRes = de.lshorizon.whatswhere.R.string.onb_title_welcome,
                descRes = de.lshorizon.whatswhere.R.string.onb_desc_welcome
            )
        )
        pages.add(
            OnboardingPage(
                imageRes = de.lshorizon.whatswhere.R.drawable.ic_search,
                titleRes = de.lshorizon.whatswhere.R.string.onb_title_organize,
                descRes = de.lshorizon.whatswhere.R.string.onb_desc_organize
            )
        )
        pages.add(
            OnboardingPage(
                imageRes = de.lshorizon.whatswhere.R.drawable.ic_qr_code,
                titleRes = de.lshorizon.whatswhere.R.string.onb_title_scan,
                descRes = de.lshorizon.whatswhere.R.string.onb_desc_scan
            )
        )
    }

    private fun setupRecycler() {
        adapter = OnboardingAdapter(pages)
        val lm = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.pager.layoutManager = lm
        binding.pager.adapter = adapter
        PagerSnapHelper().attachToRecyclerView(binding.pager)
        binding.pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = lm.findFirstVisibleItemPosition()
                    if (pos != RecyclerView.NO_POSITION) {
                        currentIndex = pos
                        updateUi()
                    }
                }
            }
        })
    }

    private fun setupDots() {
        binding.dots.removeAllViews()
        repeat(pages.size) { index ->
            val dot = View(this)
            val size = resources.displayMetrics.density * 8
            val params = android.widget.LinearLayout.LayoutParams(size.toInt(), size.toInt())
            params.marginStart = (resources.displayMetrics.density * 4).toInt()
            params.marginEnd = (resources.displayMetrics.density * 4).toInt()
            dot.layoutParams = params
            dot.background = getDrawable(de.lshorizon.whatswhere.R.drawable.ic_circle_filled)
            dot.alpha = if (index == 0) 1f else 0.3f
            binding.dots.addView(dot)
        }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            if (currentIndex < pages.size - 1) {
                currentIndex += 1
                binding.pager.smoothScrollToPosition(currentIndex)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateUi() {
        // Update dots
        for (i in 0 until binding.dots.childCount) {
            binding.dots.getChildAt(i).alpha = if (i == currentIndex) 1f else 0.3f
        }
        // Update buttons
        val isLast = currentIndex == pages.size - 1
        binding.btnNext.text = getString(
            if (isLast) de.lshorizon.whatswhere.R.string.onb_button_start
            else de.lshorizon.whatswhere.R.string.onb_button_next
        )
    }

    private fun finishOnboarding() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}

