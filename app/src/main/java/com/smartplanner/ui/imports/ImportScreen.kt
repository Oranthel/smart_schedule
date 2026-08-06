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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("导入与确认") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = csvText,
                onValueChange = { csvText = it },
                label = { Text("粘贴 CSV（标题,类型,开始,结束,位置）") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.importCsv(csvText); csvText = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("导入并解析")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("快速记录（自然语言）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.quickNote(noteText); noteText = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("记录")
            }

            if (state.pendingParseCount > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "离线解析队列：${state.pendingParseCount} 条待联网重试",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("导入批次", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
            Text(
                "${batch.parsedCount} 项  ·  ${timeFmt.format(Date(batch.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!batch.confirmed && pending.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                pending.take(6).forEach { item ->
                    Text(
                        "· ${item.title}  (${(item.confidence * 100).toInt()}%)" +
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
