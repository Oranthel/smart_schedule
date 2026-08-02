package com.smartplanner.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartplanner.core.data.repo.DayRow
import com.smartplanner.core.data.repo.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 周视图 UI 状态：本周一起 7 天的日计划。 */
data class WeekUiState(
    val weekStart: Long = thisWeekStart(),
    val days: List<Pair<Long, List<DayRow>>> = emptyList(),
) {
    companion object {
        fun thisWeekStart(): Long =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    }
}

/** 周视图 ViewModel：按周聚合 7 天日计划，支持翻周。 */
class WeekViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val weekStart = MutableStateFlow(WeekUiState.thisWeekStart())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeekUiState> = weekStart.flatMapLatest { start ->
        val flows = (0..6).map { repo.observeDayPlan(start + it) }
        combine(*flows.toTypedArray()) { arrays: Array<List<DayRow>> ->
            WeekUiState(weekStart = start, days = arrays.mapIndexed { i, rows -> (start + i) to rows })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun prevWeek() { weekStart.value -= 7 }
    fun nextWeek() { weekStart.value += 7 }
    fun thisWeek() { weekStart.value = WeekUiState.thisWeekStart() }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { WeekViewModel(repo) }
        }
    }
}
