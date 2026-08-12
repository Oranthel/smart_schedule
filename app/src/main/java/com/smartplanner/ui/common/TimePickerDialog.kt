package com.smartplanner.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable

/**
 * 时间选择对话框（滚轮式 TimePicker）。
 * 替代文本输入，避免格式错误。
 *
 * @param initialMinute 初始分钟（自午夜起，0..1439）
 * @param onConfirm 点击确认时回调，返回分钟数
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val safeInit = initialMinute.coerceIn(0, 1439)
    val state = rememberTimePickerState(
        initialHour = safeInit / 60,
        initialMinute = safeInit % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
