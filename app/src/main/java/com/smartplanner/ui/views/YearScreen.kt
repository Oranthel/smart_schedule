package com.smartplanner.ui.views

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartplanner.di.AppContainer

/**
 * 年视图：3 列 4 行的月份网格，每格显示月份与事项数，
 * 事项数映射背景色深浅；点击某月显示选中态并 Toast 提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(container: AppContainer) {
    val vm: YearViewModel = viewModel(factory = YearViewModel.factory(container.repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.yearTitle, style = MaterialTheme.typography.titleLarge) },
            actions = {
                TextButton(onClick = { vm.prevYear() }) { Text("上一年") }
                TextButton(onClick = { vm.thisYear() }) { Text("本年") }
                TextButton(onClick = { vm.nextYear() }) { Text("下一年") }
            },
        )
    }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.monthCounts.size) { idx ->
                val (month, count) = state.monthCounts[idx]
                YearMonthCell(
                    month = month,
                    count = count,
                    isSelected = selectedMonth == month,
                    baseColor = primaryContainer,
                    onClick = {
                        if (selectedMonth == month) {
                            selectedMonth = null
                        } else {
                            selectedMonth = month
                            Toast.makeText(
                                context,
                                "${state.yearTitle}${month}月 共 ${count} 项",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

/** 单月格子：月份标题 + 事项数，背景色按事项数深浅映射。 */
@Composable
private fun YearMonthCell(
    month: Int,
    count: Int,
    isSelected: Boolean,
    baseColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = countColor(count, baseColor)),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${month}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${count} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 事项数 → 背景色 alpha 映射：0 项最浅，越多越深（封顶 8 项）。 */
private fun countColor(count: Int, base: Color): Color {
    if (count <= 0) return base.copy(alpha = 0.12f)
    val ratio = minOf(count, 8) / 8f
    return base.copy(alpha = 0.25f + ratio * 0.65f)
}
