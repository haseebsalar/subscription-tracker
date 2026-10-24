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

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannel(ctx)
        val today = LocalDate.now()
        val notified = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)

        for (s in Store.load(ctx)) {
            val days = s.daysLeft(today)
            if (days != 0 && days != 1 && days != 3) continue

            val key = "${s.id}_${today}_$days"
            if (notified.contains(key)) continue

            val text = when (days) {
                0 -> ctx.getString(R.string.notif_today, s.name)
                1 -> ctx.getString(R.string.notif_tomorrow, s.name)
                else -> ctx.getString(R.string.notif_days, s.name, days)
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(ctx).notify(s.id.hashCode() + days, notification)
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
