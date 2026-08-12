package com.smartplanner.ui.imports

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.di.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(container: AppContainer) {
    val vm: ImportViewModel = viewModel(factory = ImportViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var csvText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    // 导入格式：0=CSV 1=JSON 2=Markdown 3=自然语言
    var formatIdx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val formats = listOf("CSV", "JSON", "Markdown", "自然语言")
    val formatKinds = listOf("IMPORT_CSV", "IMPORT_JSON", "IMPORT_MD", "IMPORT_TEXT")
    var showManual by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("导入与确认") }) }) { padding ->
        // 整页用 LazyColumn 可滚动，避免内嵌 LazyColumn 触发 infinity constraints 崩溃
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("导入格式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = formatIdx == i,
                            onClick = { formatIdx = i },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = formats.size),
                        ) { Text(label) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = {
                        Text(when (formatIdx) {
                            0 -> "粘贴 CSV（标题,类型,开始,结束,位置）"
                            1 -> "粘贴 JSON 数组"
                            2 -> "粘贴 Markdown 任务列表（- [ ] 标题）"
                            else -> "输入自然语言（每行一条）"
                        })
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.importText(csvText, formatKinds[formatIdx]); csvText = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("导入并解析") }
                    OutlinedButton(
                        onClick = { showManual = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("手动添加") }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { HorizontalDivider() }
            item { Spacer(Modifier.height(8.dp)) }

            item {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("快速记录（自然语言）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedButton(
                    onClick = { vm.quickNote(noteText); noteText = "" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("记录") }
            }

            if (state.pendingParseCount > 0) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "离线解析队列：${state.pendingParseCount} 条待联网重试",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            state.message?.let {
                item {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { HorizontalDivider() }
            item {
                Text("导入批次", style = MaterialTheme.typography.titleMedium)
            }

            items(state.batches, key = { it.id }) { batch ->
                BatchCard(
                    batch = batch,
                    pending = state.pendingByBatch[batch.id].orEmpty(),
                    timeFmt = timeFmt,
                    onConfirm = { vm.confirmBatch(batch.id) },
                    onUndo = { vm.undoBatch(batch.id) },
                )
            }
        }
    }

    if (showManual) {
        ManualAddDialog(
            onDismiss = { showManual = false },
            onAdd = { type, title, start, end, location, est ->
                vm.addManual(type, title, start, end, location, est)
                showManual = false
            },
        )
    }
}

@Composable
private fun BatchCard(
    batch: ImportBatch,
    pending: List<com.smartplanner.core.data.entity.ScheduleItem>,
    timeFmt: SimpleDateFormat,
    onConfirm: () -> Unit,
    onUndo: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "#${batch.id}  ${batch.sourceType}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (batch.confirmed) "已确认" else "待确认",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (batch.confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            // 安全解析 createdAt（兼容旧的 ISO 字符串与新的 millis 字符串）
            val timeText = remember(batch.createdAt) { formatBatchTime(batch.createdAt, timeFmt) }
            Text(
                "${batch.parsedCount} 项  ·  $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!batch.confirmed && pending.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                pending.take(6).forEach { item ->
                    Text(
                        "· ${item.title}  (${((item.confidence ?: 0f) * 100).toInt()}%)" +
                            if (item.needsReview) "  待复核" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (pending.size > 6) Text("…共 ${pending.size} 项", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm) { Text("确认并入档") }
                    OutlinedButton(onClick = onUndo) { Text("整批撤销") }
                }
            }
        }
    }
}

/** 兼容解析批次时间：优先按 millis 解析，失败回退原字符串。 */
private fun formatBatchTime(raw: String, fmt: SimpleDateFormat): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val millis = raw.toLong()
        fmt.format(Date(millis))
    }.getOrElse {
        // 旧的 ISO 字符串，尝试解析后格式化
        runCatching {
            val ldt = java.time.LocalDateTime.parse(raw)
            fmt.format(Date(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }.getOrElse { raw }
    }
}

/** 手动添加事项对话框：选择类型 + 标题 + 时间 + 地点 + 时长。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAddDialog(
    onDismiss: () -> Unit,
    onAdd: (type: com.smartplanner.core.data.model.ItemType, title: String, start: Int?, end: Int?, location: String?, est: Int?) -> Unit,
) {
    var typeIdx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val types = listOf(
        com.smartplanner.core.data.model.ItemType.FIXED,
        com.smartplanner.core.data.model.ItemType.TEMP_ACTIVITY,
        com.smartplanner.core.data.model.ItemType.TODO,
        com.smartplanner.core.data.model.ItemType.ROUTINE,
    )
    val typeLabels = listOf("固定事项", "临时活动", "代办", "作息")
    var title by remember { mutableStateOf("") }
    var hasTime by remember { mutableStateOf(true) }
    var startMin by remember { androidx.compose.runtime.mutableIntStateOf(9 * 60) }
    var endMin by remember { androidx.compose.runtime.mutableIntStateOf(10 * 60) }
    var location by remember { mutableStateOf("") }
    var est by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加事项") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    typeLabels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = typeIdx == i,
                            onClick = { typeIdx = i },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = typeLabels.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                    Text("  指定时间", style = MaterialTheme.typography.bodyMedium)
                }
                if (hasTime) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.smartplanner.ui.common.TimeField(minute = startMin, onPick = { startMin = it }, label = "起", modifier = Modifier.weight(1f))
                        com.smartplanner.ui.common.TimeField(minute = endMin, onPick = { endMin = it }, label = "止", modifier = Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = est, onValueChange = { est = it.filter { c -> c.isDigit() } }, label = { Text("预估时长(分钟)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val s = if (hasTime) startMin else null
                    val e = if (hasTime) endMin else null
                    val es = if (!hasTime) est.toIntOrNull() else null
                    onAdd(types[typeIdx], title, s, e, location.ifBlank { null }, es)
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
