package com.subtracker.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.subtracker.app.databinding.ItemSubBinding

class SubAdapter(private val onClick: (Subscription) -> Unit) :
    RecyclerView.Adapter<SubAdapter.VH>() {

    private var items: List<Subscription> = emptyList()

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
        val cycle = ctx.getString(if (s.yearly) R.string.per_year else R.string.per_month)
        val next = s.nextRenewal()

        holder.b.itemName.text =
            if (s.trial) ctx.getString(R.string.trial_name, s.name) else s.name
        holder.b.itemDetail.text = ctx.getString(
            R.string.item_detail, Prefs.money(ctx, s.price), cycle, s.category, next.toString()
        )

        val days = s.daysLeft()
        holder.b.itemDays.text = when {
            s.status == Status.PAUSED -> ctx.getString(R.string.status_paused)
            s.status == Status.CANCELLED -> ctx.getString(R.string.status_cancelled)
            s.trial && days < 0 -> ctx.getString(R.string.trial_ended)
            days == 0 -> ctx.getString(R.string.renews_today)
            days == 1 -> ctx.getString(R.string.renews_tomorrow)
            else -> ctx.getString(R.string.renews_in_days, days)
        }
        holder.b.root.alpha = if (s.status == Status.ACTIVE) 1f else 0.55f
        holder.b.root.setOnClickListener { onClick(s) }
    }
}
