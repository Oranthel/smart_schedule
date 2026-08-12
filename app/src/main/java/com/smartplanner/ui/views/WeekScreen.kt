package com.smartplanner.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.repo.DayRow
import com.smartplanner.di.AppContainer
import com.smartplanner.ui.home.formatHm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(container: AppContainer) {
    val vm: WeekViewModel = viewModel(factory = WeekViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val start = LocalDate.ofEpochDay(state.weekStart)
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("周视图", style = MaterialTheme.typography.titleLarge)
                Text("${start.format(fmt)} – ${end.format(fmt)}", style = MaterialTheme.typography.bodySmall)
            }
        }, actions = {
            TextButton(onClick = { vm.prevWeek() }) { Text("上一周") }
            TextButton(onClick = { vm.thisWeek() }) { Text("本周") }
            TextButton(onClick = { vm.nextWeek() }) { Text("下一周") }
        })
    }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶部环形图：按事项类型的时间分配
            item { TimeAllocationDonut(state.days) }

            // 每日列表
            items(state.days, key = { it.first }) { (epoch, rows) ->
                DayColumn(epoch, rows)
            }
        }
    }
}

/** 类型 → 颜色映射（与首页 TypeDot 一致）。 */
private fun typeColor(type: ItemType): Color = when (type) {
    ItemType.COURSE, ItemType.FIXED -> Color(0xFFE64A19)
    ItemType.TEMP_ACTIVITY -> Color(0xFF8E24AA)
    ItemType.ROUTINE -> Color(0xFF9E9E9E)
    ItemType.REST_BUFFER -> Color(0xFF4CAF50)
    ItemType.TODO -> Color(0xFF1E88E5)
    ItemType.GOAL_TASK -> Color(0xFFFB8C00)
}

private fun typeLabel(type: ItemType): String = when (type) {
    ItemType.COURSE -> "课程"
    ItemType.FIXED -> "固定"
    ItemType.TEMP_ACTIVITY -> "临时"
    ItemType.ROUTINE -> "作息"
    ItemType.REST_BUFFER -> "休息"
    ItemType.TODO -> "代办"
    ItemType.GOAL_TASK -> "目标"
}

/** 计算一行的时间跨度（分钟），无起止则 0。 */
private fun DayRow.minutes(): Int {
    val s = startMinute ?: return 0
    val e = endMinute ?: return 0
    return (e - s).coerceAtLeast(0)
}

/**
 * 环形图：本周按类型的时间分配（分钟）。
 * 中间显示总时长，下方图例列出各类型占比。
 */
@Composable
private fun TimeAllocationDonut(days: List<Pair<Long, List<DayRow>>>) {
    val byType = days.flatMap { it.second }
        .groupBy { it.type }
        .mapValues { (_, rows) -> rows.sumOf { it.minutes() } }
        .filter { it.value > 0 }
        .toList()
        .sortedByDescending { it.second }
    val total = byType.sumOf { it.second }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("本周时间分配", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (total == 0) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            } else {
                DonutCanvas(byType, total)
                Spacer(Modifier.height(12.dp))
                val totalH = total / 60
                val totalM = total % 60
                Text("共 ${totalH}h${totalM}m", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                // 图例
                byType.forEach { (type, mins) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).padding(end = 0.dp)) {
                            Canvas(Modifier.size(12.dp)) {
                                drawRect(typeColor(type))
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(typeLabel(type), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "%dh%dm  %d%%".format(mins / 60, mins % 60, mins * 100 / total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 用 Canvas 画环形图。 */
@Composable
private fun DonutCanvas(slices: List<Pair<ItemType, Int>>, total: Int) {
    Canvas(modifier = Modifier.size(160.dp)) {
        val diameter = min(size.width, size.height)
        val stroke = diameter * 0.22f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f // 从顶部开始

        // 背景圈
        drawArc(
            color = Color.LightGray.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )

        slices.forEach { (type, mins) ->
            val sweep = mins.toFloat() / total * 360f
            drawArc(
                color = typeColor(type),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun DayColumn(epoch: Long, rows: List<DayRow>) {
    val date = LocalDate.ofEpochDay(epoch)
    val isToday = date == LocalDate.now()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("E M/d", Locale.CHINA)),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text("${rows.size} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rows.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("无安排", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rows.take(6).forEach { r ->
                    Text(
                        "${formatHm(r.startMinute)}  ${r.title}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (rows.size > 6) Text("…共 ${rows.size} 项", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
