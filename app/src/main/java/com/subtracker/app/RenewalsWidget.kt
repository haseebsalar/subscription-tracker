package com.subtracker.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Home-screen widget listing the next three renewals. */
class RenewalsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RenewalsWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_renewals)
            val upcoming = Store.load(context)
                .filter { it.status == Status.ACTIVE && it.daysLeft() >= 0 }
                .sortedBy { it.nextRenewal() }
                .take(3)

            val rows = listOf(R.id.widgetRow1, R.id.widgetRow2, R.id.widgetRow3)
            rows.forEachIndexed { i, id ->
                if (i < upcoming.size) {
                    val s = upcoming[i]
                    val days = s.daysLeft()
                    val whenText = when (days) {
                        0 -> context.getString(R.string.renews_today)
                        1 -> context.getString(R.string.renews_tomorrow)
                        else -> context.getString(R.string.renews_in_days, days)
                    }
                    views.setViewVisibility(id, View.VISIBLE)
                    views.setTextViewText(
                        id,
                        context.getString(
                            R.string.widget_row, s.name, Prefs.money(context, s.price), whenText
                        )
                    )
                } else {
                    views.setViewVisibility(id, View.GONE)
                }
            }
            views.setViewVisibility(
                R.id.widgetEmpty,
                if (upcoming.isEmpty()) View.VISIBLE else View.GONE
            )

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)
            return views
        }
    }
}
