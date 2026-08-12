package com.smartplanner.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smartplanner.core.ai.ParseQueueWorker
import com.smartplanner.core.ai.TextParseService
import com.smartplanner.core.data.db.SmartDatabase
import com.smartplanner.core.data.prefs.UserPreferences
import com.smartplanner.core.data.repo.ScheduleRepository
import com.smartplanner.core.notifications.ReminderNotifier
import com.smartplanner.core.pomodoro.PomodoroRepository
import com.smartplanner.core.pomodoro.PomodoroTimer

/**
 * 手动依赖容器（v1.0 不引入 Hilt/Koin，保持轻量）。
 * 由 [com.smartplanner.SmartPlannerApp] 在 onCreate 持有，整个进程生命周期单例。
 *
 * 职责：构造并暴露 Database / Preferences / Repository / Notifier 等单例，
 * 供 ViewModel 与 WorkManager 复用；并在离线解析入队后触发 [ParseQueueWorker]。
 */
class AppContainer(private val context: Context) {

    val database: SmartDatabase by lazy { SmartDatabase.get(context) }

    val preferences: UserPreferences by lazy { UserPreferences(context) }

    /** 解析服务（端侧 fallback + 云端 LLM，离线自动入队）。 */
    val textParseService: TextParseService by lazy { TextParseService() }

    val repository: ScheduleRepository by lazy {
        ScheduleRepository(database, preferences) { enqueuePendingParse() }
    }

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(context, preferences) }

    /** 番茄钟记录仓库（持久化历史）。 */
    val pomodoroRepository: PomodoroRepository by lazy { PomodoroRepository(database.pomodoroDao()) }

    /** 番茄钟计时器单例（进程级，保持当前计时状态）。 */
    val pomodoroTimer: PomodoroTimer by lazy { PomodoroTimer() }

    /** 触发离线解析队列：联网后批量消费 pending_parse（附录 D.4）。 */
    private fun enqueuePendingParse() {
        val request = OneTimeWorkRequestBuilder<ParseQueueWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "parse_queue",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
