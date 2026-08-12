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

/** 年视图 UI 状态：12 个月各自的事项总数与标题。 */
data class YearUiState(
    val year: Int = LocalDate.now().year,
    val monthCounts: List<Pair<Int, Int>> = (1..12).map { it to 0 },
    val yearTitle: String = "${LocalDate.now().year}年",
)

/** 年视图 ViewModel：按年聚合 12 个月事项总数，支持上一年/下一年/本年切换。 */
class YearViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val year = MutableStateFlow(LocalDate.now().year)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<YearUiState> = year.flatMapLatest { y ->
        // 每月：聚合该月所有天的 observeDayPlan，求和 rows.size
        val monthFlows = (1..12).map { m ->
            val monthStart = LocalDate.of(y, m, 1).toEpochDay()
            val lengthOfMonth = YearMonth.of(y, m).lengthOfMonth()
            val dayFlows = (0 until lengthOfMonth).map { repo.observeDayPlan(monthStart + it) }
            combine(*dayFlows.toTypedArray()) { arrays: Array<List<DayRow>> ->
                arrays.sumOf { it.size }
            }
        }
        // 12 个月流合并为年视图状态
        combine(*monthFlows.toTypedArray()) { counts: Array<Int> ->
            YearUiState(
                year = y,
                monthCounts = (1..12).map { it to counts[it - 1] },
                yearTitle = "${y}年",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearUiState())

    /** 上一年。 */
    fun prevYear() {
        year.value = year.value - 1
    }

    /** 下一年。 */
    fun nextYear() {
        year.value = year.value + 1
    }

    /** 回到本年。 */
    fun thisYear() {
        year.value = LocalDate.now().year
    }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { YearViewModel(repo) }
        }
    }
}
