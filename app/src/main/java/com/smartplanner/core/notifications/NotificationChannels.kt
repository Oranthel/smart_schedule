package com.smartplanner.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val NORMAL = "ch_normal"
    const val IMPORTANT = "ch_important"
    const val ALARM = "ch_alarm"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(NORMAL, "普通提醒", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(IMPORTANT, "重要提醒", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(ALARM, "闹钟提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true); vibrationPattern = longArrayOf(0, 500, 250, 500)
            },
        )
        channels.forEach(nm::createNotificationChannel)
    }
}
