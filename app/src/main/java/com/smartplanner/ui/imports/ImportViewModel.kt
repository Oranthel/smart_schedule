package com.smartplanner.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.ScheduleItem
import com.smartplanner.core.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 导入页 UI 状态：批次 + 按批次分组的待确认项 + 离线队列长度 + 最近消息。 */
data class ImportUiState(
    val batches: List<ImportBatch> = emptyList(),
    val pendingByBatch: Map<Long, List<ScheduleItem>> = emptyMap(),
    val pendingParseCount: Int = 0,
    val message: String? = null,
)

/**
 * 导入与确认 ViewModel。
 * 行为：CSV 本地解析 → 入待确认（PENDING）→ 用户确认落库 / 整批撤销；
 * 快速记录走在线解析，失败入离线队列（附录 D.4）。
 */
class ImportViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ImportUiState> = combine(
        repo.observeBatches(),
        repo.observePendingItems(),
        repo.observePendingParse(),
        message,
    ) { batches, pending, pendingParse, msg ->
        ImportUiState(
            batches = batches,
            pendingByBatch = pending.filter { it.importBatchId != null }.groupBy { it.importBatchId!! },
            pendingParseCount = pendingParse.size,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    fun importCsv(text: String) = viewModelScope.launch {
        if (text.isBlank()) { message.value = "请输入 CSV 内容"; return@launch }
        val id = repo.importCsv(text)
        message.value = "已导入批次 #$id，请确认"
    }

    /** 通用导入：按格式 kind 调用对应解析器。 */
    fun importText(text: String, kind: String) = viewModelScope.launch {
        if (text.isBlank()) { message.value = "请输入内容"; return@launch }
        val id = repo.importText(text, kind)
        message.value = "已导入批次 #$id（$kind），请确认"
    }

    /** 手动添加单条事项（直接入正式日程）。 */
    fun addManual(
        type: com.smartplanner.core.data.model.ItemType,
        title: String,
        start: Int?,
        end: Int?,
        location: String?,
        est: Int?,
    ) = viewModelScope.launch {
        val today = java.time.LocalDate.now().toEpochDay()
        repo.addManualItem(type, title.trim(), start, end, location, est, today)
        message.value = "已添加：$title"
    }

    fun confirmBatch(batchId: Long) = viewModelScope.launch {
        repo.confirmBatch(batchId)
        message.value = "批次 #$batchId 已确认并入档"
    }

    fun undoBatch(batchId: Long) = viewModelScope.launch {
        repo.undoBatch(batchId)
        message.value = "批次 #$batchId 已撤销"
    }

    fun quickNote(payload: String) = viewModelScope.launch {
        if (payload.isBlank()) { message.value = "请输入内容"; return@launch }
        repo.quickNote(payload)
        message.value = "已记录，在线解析失败将自动入队"
    }

    fun clearMessage() { message.value = null }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { ImportViewModel(repo) }
        }
    }
}
