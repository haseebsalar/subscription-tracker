package com.subtracker.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.subtracker.app.databinding.ItemSubBinding
import java.util.Locale

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
        val price = String.format(Locale.getDefault(), "%.2f", s.price)
        val cycle = ctx.getString(if (s.yearly) R.string.per_year else R.string.per_month)
        val next = s.nextRenewal()

        holder.b.itemName.text = if (s.trial) ctx.getString(R.string.trial_name, s.name) else s.name
        holder.b.itemDetail.text = ctx.getString(R.string.item_detail, price, cycle, next.toString())

        val days = s.daysLeft()
        holder.b.itemDays.text = when {
            s.trial && days < 0 -> ctx.getString(R.string.trial_ended)
            days == 0 -> ctx.getString(R.string.renews_today)
            days == 1 -> ctx.getString(R.string.renews_tomorrow)
            else -> ctx.getString(R.string.renews_in_days, days)
        }
        holder.b.root.setOnClickListener { onClick(s) }
    }
}
