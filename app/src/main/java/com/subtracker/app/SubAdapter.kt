package com.subtracker.app

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.subtracker.app.databinding.ItemSubBinding
import java.time.format.DateTimeFormatter
import java.util.Locale

class SubAdapter(private val onClick: (Subscription) -> Unit) :
    RecyclerView.Adapter<SubAdapter.VH>() {

    private var items: List<Subscription> = emptyList()
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun submit(list: List<Subscription>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemSubBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSubBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val ctx = holder.b.root.context
        val next = s.nextRenewal()

        holder.b.itemAvatar.text = s.name.trim().take(1).uppercase().ifEmpty { "?" }
        holder.b.itemAvatar.backgroundTintList = ColorStateList.valueOf(Categories.color(s.category))
        holder.b.itemName.text = s.name
        holder.b.itemPrice.text = Prefs.money(ctx, s.price)

        val dateText = next.format(dateFormat)
        holder.b.itemDetail.text = if (s.trial) {
            ctx.getString(R.string.item_meta_trial, s.category, dateText)
        } else {
            val cycle = ctx.getString(if (s.yearly) R.string.yearly else R.string.monthly)
            ctx.getString(R.string.item_meta, s.category, cycle, dateText)
        }

        val days = s.daysLeft()
        val text: String
        val colorRes: Int
        when {
            s.status == Status.PAUSED -> {
                text = ctx.getString(R.string.status_paused); colorRes = R.color.neutral
            }
            s.status == Status.CANCELLED -> {
                text = ctx.getString(R.string.status_cancelled); colorRes = R.color.neutral
            }
            s.trial && days < 0 -> {
                text = ctx.getString(R.string.trial_ended); colorRes = R.color.neutral
            }
            days == 0 -> {
                text = ctx.getString(R.string.renews_today); colorRes = R.color.danger
            }
            days == 1 -> {
                text = ctx.getString(R.string.renews_tomorrow); colorRes = R.color.danger
            }
            else -> {
                text = ctx.getString(R.string.renews_in_days, days)
                colorRes = when {
                    days <= 3 -> R.color.danger
                    days <= 7 -> R.color.warning
                    else -> R.color.success
                }
            }
        }
        val color = ContextCompat.getColor(ctx, colorRes)
        holder.b.itemDays.text = text
        holder.b.itemDays.setTextColor(color)
        holder.b.itemDays.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 36))

        holder.b.root.alpha = if (s.status == Status.ACTIVE) 1f else 0.6f
        holder.b.root.setOnClickListener { onClick(s) }
    }
}
