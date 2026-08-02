package com.smartplanner.ui.views

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.core.data.repo.DayRow
import com.smartplanner.di.AppContainer
import com.smartplanner.ui.home.formatHm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            items(state.days, key = { it.first }) { (epoch, rows) ->
                DayColumn(epoch, rows)
            }
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
