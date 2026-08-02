package com.smartplanner.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒调度（PRD 第十四章）。
 * - 普通提醒 NORMAL；重要提醒 IMPORTANT（课程/会议/截止，带上下文）。
 * - 勿扰：睡眠时段只允许"闹钟级 + 用户显式夜间开关"打断（PRD §14 v1.1 修订）。
 */
class ReminderNotifier(private val context: Context, private val prefs: UserPreferences) {

    private val am get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun schedule(
        itemId: Long,
        title: String,
        text: String,
        at: LocalDateTime,
        type: ItemType,
        isAlarmLevel: Boolean = false,
    ) {
        val triggerMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) return

        // 勿扰/夜间判断
        val dndStart = prefs.dndStart.first()
        val dndEnd = prefs.dndEnd.first()
        val minute = at.toLocalTime().toSecondOfDay() / 60
        val inNight = isInDnd(minute, dndStart, dndEnd)
        if (inNight) {
            val nightInterrupt = prefs.nightInterrupt.first()
            if (!(isAlarmLevel && nightInterrupt)) return // 静默跳过（v1.0；后续可改为延后补推）
        }

        val channel = when {
            isAlarmLevel -> NotificationChannels.ALARM
            type == ItemType.COURSE || type == ItemType.FIXED -> NotificationChannels.IMPORTANT
            else -> NotificationChannels.NORMAL
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_CHANNEL, channel)
        }
        val pi = PendingIntent.getBroadcast(
            context, itemId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
    }

    fun cancel(itemId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, itemId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.cancel(pi)
    }

    /** 判断 minute 是否落在 [dndStart, dndEnd)（支持跨午夜，如 22:00→07:00）。 */
    private fun isInDnd(minute: Int, dndStart: Int, dndEnd: Int): Boolean =
        if (dndStart <= dndEnd) minute in dndStart until dndEnd
        else minute >= dndStart || minute < dndEnd

    @Suppress("unused") private fun today() = LocalDate.now().toEpochDay()
}
