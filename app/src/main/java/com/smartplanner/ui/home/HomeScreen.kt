package com.smartplanner.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.data.repo.DayRow
import com.smartplanner.di.AppContainer
import com.smartplanner.ui.common.TimeField
import com.smartplanner.ui.pomodoro.PomodoroDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 把"自午夜起的分钟"格式化为 HH:mm。 */
fun formatHm(minute: Int?): String =
    if (minute == null) "—" else String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    // 番茄钟目标（事项 id + 标题），非空时显示倒计时对话框
    var pomodoroTarget by remember { mutableStateOf<Pair<Long?, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日日程", style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "添加") },
                text = { Text("添加") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            ModeSwitcher(state.mode, vm::setMode)

            Spacer(Modifier.height(12.dp))
            NextUpCard(state.nextRow, state.freeRatio, state.openConflicts)
            if (state.openConflicts > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ 检测到 ${state.openConflicts} 个冲突，请调整固定事项时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("时间线", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.rows, key = { (it.itemId?.toString() ?: "r") + "-" + it.title + "-" + (it.startMinute ?: -1) }) { row ->
                    DayRowCard(
                        row = row,
                        onDone = vm::markDone,
                        onSkip = vm::skip,
                        onPostpone = vm::postpone,
                        onStart = { pomodoroTarget = (row.itemId to row.title) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddItemDialog(
            onDismiss = { showAdd = false },
            onAddTemp = { title, start, dur -> vm.addTempActivity(title, start, dur); showAdd = false },
            onAddTodo = { title, est -> vm.addTodo(title, est); showAdd = false },
        )
    }

    pomodoroTarget?.let { (id, title) ->
        PomodoroDialog(
            timer = container.pomodoroTimer,
            repo = container.pomodoroRepository,
            itemId = id,
            title = title,
            onDismiss = { pomodoroTarget = null },
        )
    }
}

@Composable
private fun ModeSwitcher(selected: ScheduleMode, onSelect: (ScheduleMode) -> Unit) {
    val options = ScheduleMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        for ((i, m) in options.withIndex()) {
            SegmentedButton(
                selected = m == selected,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(when (m) {
                ScheduleMode.CONSERVATIVE -> "保守"
                ScheduleMode.BALANCED -> "平衡"
                ScheduleMode.ACTIVE -> "进取"
            }) }
        }
    }
}

@Composable
private fun NextUpCard(next: DayRow?, freeRatio: Float, conflicts: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("下一项", style = MaterialTheme.typography.labelMedium)
            if (next != null) {
                Text(
                    "${next.title}  ·  ${formatHm(next.startMinute)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("今日暂无待执行项", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "保留空闲比例 %.0f%%  ·  未解决冲突 %d".format(freeRatio * 100, conflicts),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DayRowCard(
    row: DayRow,
    onDone: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onPostpone: (Long) -> Unit,
    onStart: () -> Unit,
) {
    val actionable = row.itemId != null && row.status in setOf(ItemStatus.SCHEDULED, ItemStatus.PENDING, ItemStatus.IN_PROGRESS)
    val finished = row.status in setOf(ItemStatus.DONE, ItemStatus.SKIPPED, ItemStatus.OVERDUE)
    val dimColor = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (finished) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeDot(row.type, row.rest)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge,
                        color = dimColor,
                        textDecoration = if (finished) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    Text(
                        "${formatHm(row.startMinute)} – ${formatHm(row.endMinute)}" +
                            (row.location?.let { "  ·  $it" } ?: "") +
                            statusSuffix(row.status, row.needsReview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (actionable && row.type != ItemType.REST_BUFFER) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStart) { Text("开始") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onDone(row.itemId!!) }) { Text("完成") }
                    TextButton(onClick = { onSkip(row.itemId!!) }) { Text("跳过") }
                    TextButton(onClick = { onPostpone(row.itemId!!) }) { Text("延后") }
                }
            }
        }
    }
}

private fun statusSuffix(status: ItemStatus, needsReview: Boolean): String {
    val base = when (status) {
        ItemStatus.DONE -> "  ·  已完成"
        ItemStatus.SKIPPED -> "  ·  已跳过"
        ItemStatus.UNSCHEDULED -> "  ·  待排"
        ItemStatus.OVERDUE -> "  ·  超期"
        ItemStatus.IN_PROGRESS -> "  ·  进行中"
        else -> ""
    }
    return base + if (needsReview) "  ·  待确认" else ""
}

@Composable
private fun TypeDot(type: ItemType, rest: Boolean) {
    val color = when {
        rest -> Color(0xFF4CAF50)
        type == ItemType.COURSE || type == ItemType.FIXED -> Color(0xFFE64A19)
        type == ItemType.TEMP_ACTIVITY -> Color(0xFF8E24AA)
        type == ItemType.ROUTINE -> Color(0xFF9E9E9E)
        type == ItemType.REST_BUFFER -> Color(0xFF4CAF50)
        else -> Color(0xFF1E88E5)
    }
    Box(
        Modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(50)),
    )
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onAddTemp: (title: String, startMin: Int, durationMin: Int) -> Unit,
    onAddTodo: (title: String, estMinutes: Int) -> Unit,
) {
    var isTemp by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var startMin by remember { mutableIntStateOf(9 * 60) }
    var duration by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速添加") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isTemp, onClick = { isTemp = true }, label = { Text("临时活动") })
                    FilterChip(selected = !isTemp, onClick = { isTemp = false }, label = { Text("代办") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (isTemp) {
                    TimeField(minute = startMin, onPick = { startMin = it }, label = "开始时间")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("时长(分钟)") }, singleLine = true)
                } else {
                    OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("预估时长(分钟)") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && duration.toIntOrNull()?.let { it > 0 } == true,
                onClick = {
                    if (isTemp) {
                        onAddTemp(title, startMin, duration.toInt())
                    } else {
                        onAddTodo(title, duration.toInt())
                    }
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
