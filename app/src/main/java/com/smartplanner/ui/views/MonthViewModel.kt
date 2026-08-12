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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 月视图 UI 状态：该月1号起每天的事项列表与标题。 */
data class MonthUiState(
    val monthStart: Long = thisMonthStart(),
    val days: List<Pair<Long, List<DayRow>>> = emptyList(),
    val monthTitle: String = formatTitle(thisMonthStart()),
) {
    companion object {
        /** 当月1号的 epochDay。 */
        fun thisMonthStart(): Long =
            LocalDate.now().withDayOfMonth(1).toEpochDay()

        /** 将 monthStart 格式化为 "yyyy年M月"。 */
        fun formatTitle(monthStart: Long): String =
            LocalDate.ofEpochDay(monthStart)
                .format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))
    }
}

/** 月视图 ViewModel：按月聚合当月每天日计划，支持上一月/下一月/本月切换。 */
class MonthViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val monthStart = MutableStateFlow(MonthUiState.thisMonthStart())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthUiState> = monthStart.flatMapLatest { start ->
        val lengthOfMonth = YearMonth.from(LocalDate.ofEpochDay(start)).lengthOfMonth()
        val flows = (0 until lengthOfMonth).map { repo.observeDayPlan(start + it) }
        combine(*flows.toTypedArray()) { arrays: Array<List<DayRow>> ->
            MonthUiState(
                monthStart = start,
                days = arrays.mapIndexed { i, rows -> (start + i) to rows },
                monthTitle = MonthUiState.formatTitle(start),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUiState())

    /** 上一月。 */
    fun prevMonth() {
        monthStart.value = LocalDate.ofEpochDay(monthStart.value).minusMonths(1).toEpochDay()
    }

    /** 下一月。 */
    fun nextMonth() {
        monthStart.value = LocalDate.ofEpochDay(monthStart.value).plusMonths(1).toEpochDay()
    }

    /** 回到本月。 */
    fun thisMonth() {
        monthStart.value = MonthUiState.thisMonthStart()
    }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { MonthViewModel(repo) }
        }
    }
}
