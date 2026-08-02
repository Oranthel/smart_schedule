package com.smartplanner.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 计划资料页 UI 状态：作息 / 课程 / 调度偏好。 */
data class PlanUiState(
    val routines: List<RoutineRule> = emptyList(),
    val courses: List<Course> = emptyList(),
    val mode: ScheduleMode = ScheduleMode.BALANCED,
    val freeRatio: Float = 0.20f,
    val dnd: Pair<Int, Int> = (22 * 60) to (7 * 60),
    val confidence: Pair<Float, Float> = 0.70f to 0.85f,
    val message: String? = null,
)

/** 计划资料 ViewModel：基础作息/课程表 CRUD + 调度偏好设置。 */
class PlanViewModel(private val repo: ScheduleRepository) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    private val dndConfidence = combine(repo.observeDnd(), repo.observeConfidence()) { d, c -> d to c }

    private val base: StateFlow<PlanUiState> = combine(
        repo.observeActiveRoutines(),
        repo.observeActiveCourses(),
        repo.observeScheduleMode(),
        repo.observeFreeRatio(),
        dndConfidence,
    ) { routines, courses, mode, freeRatio, dc ->
        PlanUiState(routines = routines, courses = courses, mode = mode, freeRatio = freeRatio, dnd = dc.first, confidence = dc.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    val uiState: StateFlow<PlanUiState> = combine(base, message) { s, msg -> s.copy(message = msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun addRoutine(title: String, start: Int, end: Int, isSleep: Boolean) = viewModelScope.launch {
        if (title.isBlank()) { message.value = "请填写标题"; return@launch }
        repo.addRoutine(title.trim(), setOf(1, 2, 3, 4, 5, 6, 7), start, end, isSleep)
        message.value = "已添加作息：${title.trim()}"
    }

    fun deleteRoutine(id: Long) = viewModelScope.launch { repo.deleteRoutine(id) }

    fun addCourse(title: String, weekday: Int, start: Int, end: Int, classroom: String?) = viewModelScope.launch {
        if (title.isBlank()) { message.value = "请填写标题"; return@launch }
        repo.addCourse(title.trim(), weekday, start, end, classroom?.takeIf { it.isNotBlank() })
        message.value = "已添加课程：${title.trim()}"
    }

    fun archiveCourse(id: Long) = viewModelScope.launch { repo.archiveCourse(id) }

    fun setMode(mode: ScheduleMode) = viewModelScope.launch { repo.setScheduleMode(mode) }
    fun setFreeRatio(v: Float) = viewModelScope.launch { repo.setFreeRatio(v) }
    fun setDnd(start: Int, end: Int) = viewModelScope.launch { repo.setDnd(start, end) }
    fun setConfidence(low: Float, high: Float) = viewModelScope.launch { repo.setConfidence(low, high) }

    companion object {
        fun factory(repo: ScheduleRepository) = viewModelFactory {
            initializer { PlanViewModel(repo) }
        }
    }
}
