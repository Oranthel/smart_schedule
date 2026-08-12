package com.smartplanner.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.di.AppContainer
import com.smartplanner.ui.home.formatHm
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 月视图：7 列日历网格（周日为头），每格显示日期与事项数，
 * 事项数映射背景色深浅；点击某天展开/折叠显示当天事项列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(container: AppContainer) {
    val vm: MonthViewModel = viewModel(factory = MonthViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedDay by remember { mutableStateOf<Long?>(null) }

    val monthStartDate = LocalDate.ofEpochDay(state.monthStart)
    // 周日为表头：dayOfWeek.value % 7（周一=1 … 周日=7→0）
    val offset = monthStartDate.dayOfWeek.value % 7
    val lengthOfMonth = YearMonth.from(monthStartDate).lengthOfMonth()
    val today = LocalDate.now().toEpochDay()
    val weekHeaders = listOf("日", "一", "二", "三", "四", "五", "六")
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.monthTitle, style = MaterialTheme.typography.titleLarge) },
            actions = {
                TextButton(onClick = { vm.prevMonth() }) { Text("上一月") }
                TextButton(onClick = { vm.thisMonth() }) { Text("本月") }
                TextButton(onClick = { vm.nextMonth() }) { Text("下一月") }
            },
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 星期标题行
            Row(Modifier.fillMaxWidth()) {
                weekHeaders.forEach { h ->
                    Text(
                        h,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // 日历网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 前置空格补齐当月1号的星期
                items(offset) { Spacer(Modifier.fillMaxWidth().aspectRatio(1f)) }
                // 每日格子
                items(lengthOfMonth) { idx ->
                    val dayEpoch = state.monthStart + idx
                    val rows = state.days.getOrNull(idx)?.second ?: emptyList()
                    MonthDayCell(
                        day = idx + 1,
                        count = rows.size,
                        isToday = dayEpoch == today,
                        isSelected = selectedDay == dayEpoch,
                        baseColor = primaryContainer,
                        onClick = {
                            selectedDay = if (selectedDay == dayEpoch) null else dayEpoch
                        },
                    )
                }
            }

            // 选中天的展开详情
            val sel = selectedDay
            if (sel != null) {
                val rows = state.days.firstOrNull { it.first == sel }?.second ?: emptyList()
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            LocalDate.ofEpochDay(sel)
                                .format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (rows.isEmpty()) {
                            Text(
                                "无安排",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            rows.forEach { r ->
                                Text(
                                    "${formatHm(r.startMinute)}  ${r.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单日格子：日期数字 + 事项数，背景色按事项数深浅映射。 */
@Composable
private fun MonthDayCell(
    day: Int,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    baseColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = countColor(count, baseColor)),
        border = when {
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            else -> null
        },
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                "$day",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (count > 0) {
                Text(
                    "${count}项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 事项数 → 背景色 alpha 映射：0 项最浅，越多越深（封顶 8 项）。 */
private fun countColor(count: Int, base: Color): Color {
    if (count <= 0) return base.copy(alpha = 0.12f)
    val ratio = minOf(count, 8) / 8f
    return base.copy(alpha = 0.25f + ratio * 0.65f)
}
