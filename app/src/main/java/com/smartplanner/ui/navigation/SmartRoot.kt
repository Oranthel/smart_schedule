package com.smartplanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartplanner.di.AppContainer
import com.smartplanner.ui.home.HomeScreen
import com.smartplanner.ui.imports.ImportScreen
import com.smartplanner.ui.plan.PlanScreen
import com.smartplanner.ui.views.MonthScreen
import com.smartplanner.ui.views.WeekScreen
import com.smartplanner.ui.views.YearScreen

/** 顶层路由（日/周/月/年四视图 + 导入 + 计划资料）。 */
enum class SmartRoute(val title: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    IMPORT("导入"),
    PLAN("计划"),
}

/**
 * 单 Activity 架构的根：底部导航 + NavHost。
 * 六个目的地共享同一 [AppContainer]，各自取所需依赖构造 ViewModel。
 */
@Composable
fun SmartRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(bottomBar = {
        NavigationBar {
            SmartRoute.entries.forEach { route ->
                val selected = current?.hierarchy?.any { it.route == route.name } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(route.name) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = when (route) {
                                SmartRoute.DAY -> Icons.Outlined.CalendarViewDay
                                SmartRoute.WEEK -> Icons.Outlined.CalendarViewWeek
                                SmartRoute.MONTH -> Icons.Outlined.CalendarViewMonth
                                SmartRoute.YEAR -> Icons.Outlined.CalendarMonth
                                SmartRoute.IMPORT -> Icons.Outlined.NoteAdd
                                SmartRoute.PLAN -> Icons.Outlined.Tune
                            },
                            contentDescription = route.title,
                        )
                    },
                    label = { Text(route.title) },
                )
            }
        }
    }) { inner ->
        NavHost(
            navController = nav,
            startDestination = SmartRoute.DAY.name,
            modifier = Modifier.padding(inner),
        ) {
            composable(SmartRoute.DAY.name) { HomeScreen(container) }
            composable(SmartRoute.WEEK.name) { WeekScreen(container) }
            composable(SmartRoute.MONTH.name) { MonthScreen(container) }
            composable(SmartRoute.YEAR.name) { YearScreen(container) }
            composable(SmartRoute.IMPORT.name) { ImportScreen(container) }
            composable(SmartRoute.PLAN.name) { PlanScreen(container) }
        }
    }
}
