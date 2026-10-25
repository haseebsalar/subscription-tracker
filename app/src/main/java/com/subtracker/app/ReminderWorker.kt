package com.subtracker.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        RenewalsWidget.updateAll(ctx)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        // Not yet time today.
        val due = LocalTime.of(Prefs.reminderHour(ctx), Prefs.reminderMinute(ctx))
        if (LocalTime.now().isBefore(due)) return Result.success()

        createChannel(ctx)
        val today = LocalDate.now()
        val leads = Prefs.leadDays(ctx)
        val notified = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        if (notified.all.size > 300) notified.edit().clear().apply()

        for (s in Store.load(ctx)) {
            if (s.status != Status.ACTIVE) continue
            val days = s.daysLeft(today)
            if (days !in leads) continue

            val key = "${s.id}_${today}_$days"
            if (notified.contains(key)) continue

            val price = Prefs.money(ctx, s.price)
            val text = if (s.trial) {
                when (days) {
                    0 -> ctx.getString(R.string.notif_trial_today, s.name)
                    1 -> ctx.getString(R.string.notif_trial_tomorrow, s.name)
                    else -> ctx.getString(R.string.notif_trial_days, s.name, days)
                }
            } else {
                when (days) {
                    0 -> ctx.getString(R.string.notif_today, s.name, price)
                    1 -> ctx.getString(R.string.notif_tomorrow, s.name, price)
                    else -> ctx.getString(R.string.notif_days, s.name, days, price)
                }
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(ctx).notify("${s.id}_$days".hashCode(), notification)
            notified.edit().putBoolean(key, true).apply()
        }
        return Result.success()
    }

    private fun createChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "renewals"
    }
}
