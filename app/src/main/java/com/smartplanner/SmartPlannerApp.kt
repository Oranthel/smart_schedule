package com.smartplanner

import android.app.Application
import com.smartplanner.di.AppContainer

/**
 * 应用入口。持有手动 DI 容器 [AppContainer]。
 */
class SmartPlannerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.smartplanner.core.notifications.NotificationChannels.ensure(this)
    }
}
