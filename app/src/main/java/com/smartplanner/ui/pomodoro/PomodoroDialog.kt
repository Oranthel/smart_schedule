package com.smartplanner.ui.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartplanner.core.pomodoro.PomodoroRepository
import com.smartplanner.core.pomodoro.PomodoroState
import com.smartplanner.core.pomodoro.PomodoroTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 番茄钟 ViewModel：驱动每秒 tick，并在 WORK 完成时持久化记录。
 */
class PomodoroViewModel(
    private val timer: PomodoroTimer,
    private val repo: PomodoroRepository,
) : ViewModel() {

    private var state = mutableStateOf(timer.state())
    val current: PomodoroState get() = state.value

    /** 当前关联的任务（用于持久化记录）。 */
    var currentItem: Pair<Long?, String> = null to ""

    fun start(itemId: Long?, title: String) {
        currentItem = itemId to title
        timer.start()
        if (state.value.phase == com.smartplanner.core.pomodoro.Phase.WORK &&
            state.value.remainingSeconds == 25 * 60 && state.value.completedCount == 0
        ) {
            // 首次启动，确保从 WORK 开始
        }
        state.value = timer.state()
        launchLoop()
    }

    fun pause() { timer.pause(); state.value = timer.state() }
    fun resume() { timer.start(); state.value = timer.state(); launchLoop() }
    fun reset() { timer.reset(); state.value = timer.state() }
    fun skip() { timer.skip(); state.value = timer.state() }

    private var looping = false
    private fun launchLoop() {
        if (looping) return
        looping = true
        viewModelScope.launch {
            while (state.value.running) {
                delay(1000)
                val s = timer.tick()
                state.value = s
                if (s.justCompletedWork) {
                    // 持久化本次专注记录
                    repo.recordCompletion(
                        sourceItemId = currentItem.first,
                        title = currentItem.second,
                        durationMinutes = 25,
                        interrupted = false,
                    )
                }
            }
            looping = false
        }
    }

    /** 标记中断并停止（用户提前退出 WORK）。 */
    fun stopInterrupted() {
        if (state.value.phase == com.smartplanner.core.pomodoro.Phase.WORK && state.value.running) {
            viewModelScope.launch {
                val elapsed = 25 * 60 - state.value.remainingSeconds
                if (elapsed > 0) {
                    repo.recordCompletion(
                        sourceItemId = currentItem.first,
                        title = currentItem.second,
                        durationMinutes = elapsed / 60,
                        interrupted = true,
                    )
                }
            }
        }
        timer.reset()
        state.value = timer.state()
    }

    companion object {
        fun factory(timer: PomodoroTimer, repo: PomodoroRepository) = viewModelFactory {
            initializer { PomodoroViewModel(timer, repo) }
        }
    }
}

/**
 * 番茄钟对话框：倒计时 + 开始/暂停/跳过/停止。
 */
@Composable
fun PomodoroDialog(
    timer: PomodoroTimer,
    repo: PomodoroRepository,
    itemId: Long?,
    title: String,
    onDismiss: () -> Unit,
) {
    val vm: PomodoroViewModel = viewModel(factory = PomodoroViewModel.factory(timer, repo))
    val s = vm.current

    // 首次进入若未运行，自动启动
    LaunchedEffect(title) {
        if (!vm.current.running && vm.current.phase == com.smartplanner.core.pomodoro.Phase.WORK && vm.current.remainingSeconds == 25 * 60) {
            vm.start(itemId, title)
        }
    }

    AlertDialog(
        onDismissRequest = {
            vm.stopInterrupted()
            onDismiss()
        },
        title = { Text("番茄钟 · $title") },
        text = {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val phaseLabel = when (s.phase) {
                    com.smartplanner.core.pomodoro.Phase.WORK -> "专注中"
                    com.smartplanner.core.pomodoro.Phase.SHORT_BREAK -> "短休息"
                    com.smartplanner.core.pomodoro.Phase.LONG_BREAK -> "长休息"
                }
                Text(phaseLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "%02d:%02d".format(s.remainingSeconds / 60, s.remainingSeconds % 60),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text("今日完成 ${s.completedCount} 个", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (s.running) vm.pause() else vm.resume() }) {
                    Text(if (s.running) "暂停" else "继续")
                }
                OutlinedButton(onClick = { vm.skip() }) { Text("跳过") }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                vm.stopInterrupted()
                onDismiss()
            }) { Text("结束") }
        },
    )
}
