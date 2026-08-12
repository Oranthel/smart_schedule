package com.smartplanner.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * 时间输入字段（只读 + 点击弹滚轮 TimePicker）。
 * 替代纯文本输入，杜绝格式错误。
 *
 * @param minute 当前分钟（自午夜起）
 * @param onPick 选择后的回调
 * @param label 输入框标签
 */
@Composable
fun TimeField(
    minute: Int,
    onPick: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val safe = minute.coerceIn(0, 1439)
    OutlinedTextField(
        value = String.format(Locale.getDefault(), "%02d:%02d", safe / 60, safe % 60),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        singleLine = true,
        modifier = modifier.clickable { showDialog = true },
    )
    if (showDialog) {
        TimePickerDialog(
            initialMinute = safe,
            onConfirm = { onPick(it); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}
