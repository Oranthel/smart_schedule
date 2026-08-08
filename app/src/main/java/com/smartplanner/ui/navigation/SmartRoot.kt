package com.smartplanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
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
import com.smartplanner.ui.views.WeekScreen

/** 顶层路由（对齐 PRD 四主视图：今日/导入/计划资料/周视图）。 */
enum class SmartRoute(val title: String) {
    HOME("今日"),
    IMPORT("导入"),
    PLAN("计划资料"),
    WEEK("周视图"),
}

/**
 * 单 Activity 架构的根：底部导航 + NavHost。
 * 四个目的地共享同一 [AppContainer]，各自取所需依赖构造 ViewModel。
 */
@Composable
fun SmartRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(bottomBar = {
        NavigationBar {
            SmartRoute.values().forEach { route ->
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
                                SmartRoute.HOME -> Icons.Outlined.Home
                                SmartRoute.IMPORT -> Icons.Outlined.NoteAdd
                                SmartRoute.PLAN -> Icons.Outlined.Tune
                                SmartRoute.WEEK -> Icons.Outlined.CalendarMonth
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
            startDestination = SmartRoute.HOME.name,
            modifier = Modifier.padding(inner),
        ) {
            composable(SmartRoute.HOME.name) { HomeScreen(container) }
            composable(SmartRoute.IMPORT.name) { ImportScreen(container) }
            composable(SmartRoute.PLAN.name) { PlanScreen(container) }
            composable(SmartRoute.WEEK.name) { WeekScreen(container) }
        }
    }
}
