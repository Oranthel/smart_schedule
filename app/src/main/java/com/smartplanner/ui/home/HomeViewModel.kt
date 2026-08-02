package com.smartplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.data.repo.DayRow
import com.smartplanner.core.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** 首页 UI 状态：今日时间线 + 下一项 + 调度模式 + 空闲比例 + 冲突数。 */
data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val rows: List<DayRow> = emptyList(),
    val mode: ScheduleMode = ScheduleMode.BALANCED,
    val freeRatio: Float = 0.20f,
    val nextRow: DayRow? = null,
    val openConflicts: Int = 0,
    val loading: Boolean = true,
)

/**
 * 首页执行中心 ViewModel。
 * 聚合 [ScheduleRepository] 的日视图、偏好与冲突流，并暴露完成/跳过/延后/快速添加/重排等动作。
 */
class HomeViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val todayEpoch: Long = LocalDate.now().toEpochDay()

    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> =
        combine(
            repo.observeDayPlan(todayEpoch),
            repo.observeScheduleMode(),
            repo.observeFreeRatio(),
            repo.observeConflicts(),
            loading,
        ) { rows, mode, freeRatio, conflicts, isLoading ->
            val now = LocalTime.now().toSecondOfDay() / 60
            val next = rows.firstOrNull { r ->
                val s = r.startMinute
                s != null && s >= now && r.status in setOf(ItemStatus.SCHEDULED, ItemStatus.PENDING) && !r.rest
            }
            loading.value = false
            HomeUiState(
                date = LocalDate.now(),
                rows = rows,
                mode = mode,
                freeRatio = freeRatio,
                nextRow = next,
                openConflicts = conflicts.size,
                loading = isLoading,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun markDone(id: Long) = viewModelScope.launch { repo.markDone(id) }
    fun skip(id: Long) = viewModelScope.launch { repo.skip(id) }
    fun postpone(id: Long) = viewModelScope.launch { repo.postpone(id) }

    fun addTempActivity(title: String, start: Int, durationMin: Int) = viewModelScope.launch {
        repo.addTempActivity(todayEpoch, title.trim(), start, start + durationMin)
    }

    fun addTodo(title: String, estMinutes: Int) = viewModelScope.launch {
        repo.addTodo(title.trim(), estMinutes)
    }

    fun setMode(mode: ScheduleMode) = viewModelScope.launch { repo.setScheduleMode(mode) }

    fun regenerate() = viewModelScope.launch { repo.regenerateDay(todayEpoch) }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { HomeViewModel(repo) }
        }
    }
}
