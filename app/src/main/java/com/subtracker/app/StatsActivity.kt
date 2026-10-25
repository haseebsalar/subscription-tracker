package com.subtracker.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.subtracker.app.databinding.ActivityStatsBinding
import com.subtracker.app.databinding.ItemCategoryBinding

class StatsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val all = Store.load(this)
        val active = all.filter { it.status == Status.ACTIVE && !it.trial }
        val monthly = active.sumOf { it.monthlyCost() }
        val next30 = active.filter { it.daysLeft() in 0..30 }.sumOf { it.price }
        val saved = all.filter { it.status == Status.CANCELLED }.sumOf { it.monthlyCost() }

        binding.statMonthly.text = Prefs.money(this, monthly)
        binding.statYearly.text = Prefs.money(this, monthly * 12)
        binding.statNext30.text = Prefs.money(this, next30)
        binding.statSaved.text = Prefs.money(this, saved)

        val byCategory = active
            .groupBy { it.category }
            .map { (cat, subs) -> cat to subs.sumOf { it.monthlyCost() } }
            .sortedByDescending { it.second }

        binding.donut.setData(byCategory.map { it.second.toFloat() to Categories.color(it.first) })

        binding.categoryContainer.removeAllViews()
        if (byCategory.isEmpty()) {
            binding.categoryEmpty.visibility = View.VISIBLE
        } else {
            binding.categoryEmpty.visibility = View.GONE
            byCategory.forEach { (cat, amount) ->
                val row = ItemCategoryBinding.inflate(
                    LayoutInflater.from(this), binding.categoryContainer, false
                )
                val color = Categories.color(cat)
                val pct = if (monthly > 0) (amount / monthly * 100).toInt() else 0
                row.catName.text = getString(R.string.category_share, cat, pct)
                row.catAmount.text = Prefs.money(this, amount)
                row.catDot.backgroundTintList = ColorStateList.valueOf(color)
                row.catBar.setIndicatorColor(color)
                row.catBar.setProgressCompat(pct, false)
                binding.categoryContainer.addView(row.root)
            }
        }
    }
}
