package com.smartplanner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.di.AppContainer
import java.util.Locale

private fun parseHm(s: String): Int? {
    val p = s.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return null
    val m = p.getOrNull(1)?.toIntOrNull() ?: 0
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun fmtHm(min: Int): String = String.format(Locale.getDefault(), "%02d:%02d", min / 60, min % 60)

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(container: AppContainer) {
    val vm: PlanViewModel = viewModel(factory = PlanViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("计划资料") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("作息", "课程", "偏好").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> RoutineTab(state, vm)
                1 -> CourseTab(state, vm)
                else -> PrefTab(state, vm)
            }
        }
    }
}

@Composable
private fun RoutineTab(state: PlanUiState, vm: PlanViewModel) {
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("23:00") }
    var end by remember { mutableStateOf("07:00") }
    var isSleep by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("新增作息（默认全周）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题（如：睡眠/午餐）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("起 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("止 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isSleep, onCheckedChange = { isSleep = it })
                    Spacer(Modifier.height(0.dp))
                    Text("  睡眠段（AI 不得排入任务）", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val s = parseHm(start); val e = parseHm(end)
                    if (s != null && e != null) vm.addRoutine(title, s, e, isSleep)
                }, modifier = Modifier.fillMaxWidth()) { Text("添加") }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.routines, key = { it.id }) { r ->
                RoutineRow(r, onDelete = { vm.deleteRoutine(r.id) })
            }
        }
    }
}

@Composable
private fun RoutineRow(r: RoutineRule, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.Medium)
                Text(
                    "${fmtHm(r.startMinute)} – ${fmtHm(r.endMinute)}" + if (r.aiNoSchedule) "  ·  睡眠段" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun CourseTab(state: PlanUiState, vm: PlanViewModel) {
    var title by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(1) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("09:40") }
    var room by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("新增课程", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("课程名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("起") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("止") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEKDAYS.forEachIndexed { i, label ->
                        val wd = i + 1
                        SegmentedButton(
                            selected = weekday == wd,
                            onClick = { weekday = wd },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 7),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("教室（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val s = parseHm(start); val e = parseHm(end)
                    if (s != null && e != null) vm.addCourse(title, weekday, s, e, room)
                }, modifier = Modifier.fillMaxWidth()) { Text("添加") }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.courses, key = { it.id }) { c ->
                CourseRow(c, onArchive = { vm.archiveCourse(c.id) })
            }
        }
    }
}

@Composable
private fun CourseRow(c: Course, onArchive: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, fontWeight = FontWeight.Medium)
                Text(
                    "周${WEEKDAYS[c.weekday - 1]}  ${fmtHm(c.startMinute)}–${fmtHm(c.endMinute)}" +
                        (c.classroom?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onArchive) { Text("归档") }
        }
    }
}

@Composable
private fun PrefTab(state: PlanUiState, vm: PlanViewModel) {
    var dndStart by remember(state.dnd.first) { mutableStateOf(fmtHm(state.dnd.first)) }
    var dndEnd by remember(state.dnd.second) { mutableStateOf(fmtHm(state.dnd.second)) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("调度模式", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ScheduleMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = m == state.mode,
                                onClick = { vm.setMode(m) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            ) { Text(when (m) {
                                ScheduleMode.CONSERVATIVE -> "保守"
                                ScheduleMode.BALANCED -> "平衡"
                                ScheduleMode.ACTIVE -> "进取"
                            }) }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("保留空闲比例：%.0f%%".format(state.freeRatio * 100), style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = state.freeRatio,
                        onValueChange = { vm.setFreeRatio(it) },
                        valueRange = 0.05f..0.5f,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("勿扰时段（仅闹钟级+夜间开关可打断）", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = dndStart, onValueChange = { dndStart = it }, label = { Text("起 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dndEnd, onValueChange = { dndEnd = it }, label = { Text("止 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val s = parseHm(dndStart); val e = parseHm(dndEnd)
                        if (s != null && e != null) vm.setDnd(s, e)
                    }, modifier = Modifier.fillMaxWidth()) { Text("保存勿扰") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("置信度门控：低 %.2f / 高 %.2f".format(state.confidence.first, state.confidence.second), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("低阈值（≥写入待复核）", style = MaterialTheme.typography.bodySmall)
                    Slider(value = state.confidence.first, onValueChange = { vm.setConfidence(it, maxOf(it, state.confidence.second)) }, valueRange = 0f..1f)
                    Text("高阈值（≥直接写入）", style = MaterialTheme.typography.bodySmall)
                    Slider(value = state.confidence.second, onValueChange = { vm.setConfidence(minOf(it, state.confidence.first), it) }, valueRange = 0f..1f)
                }
            }
        }
        state.message?.let {
            item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
