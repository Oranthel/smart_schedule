package com.smartplanner.core.scheduler.model

import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.ConflictType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.ScheduleMode

/** 任务强度（休息时长计算用，PRD 第十章）。 */
enum class Intensity { LOW, MEDIUM, HIGH }

/**
 * 已落座的时间块（固定/课程/临时活动/作息/睡眠）。
 * [aiNoSchedule]=true 表示 AI 不得将灵活任务排入此段（睡眠段）。
 */
data class TimeBlock(
    val id: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val priority: Int,
    val fixedness: Fixedness,
    val type: ItemType,
    val location: String? = null,
    val aiNoSchedule: Boolean = false,
    val sourceItemId: Long? = null,
)

/** 待安排的灵活任务（代办/目标拆分/可选）。 */
data class FlexibleTask(
    val id: String,
    val title: String,
    val estMinutes: Int,
    val priority: Int,
    val deadlineEpochDay: Long? = null,
    val intensity: Intensity = Intensity.MEDIUM,
    val sourceItemId: Long? = null,
)

/** 日程条目（引擎产出）。 */
data class ScheduleEntry(
    val id: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val priority: Int,
    val type: ItemType,
    val location: String? = null,
    val rest: Boolean = false,
    val coversRoutine: Boolean = false,
    val sourceItemId: Long? = null,
)

data class ConflictFinding(
    val type: ConflictType,
    val itemIds: Set<String>,
    val reason: String,
)

data class ChangeRecord(
    val changeType: ChangeType,
    val reason: String,
    val affectedSourceIds: Set<Long> = emptySet(),
    val before: String? = null,
    val after: String? = null,
)

data class DayPlanInput(
    val dateEpochDay: Long,
    val anchors: List<TimeBlock>,          // 含固定/课程/临时活动/作息/睡眠
    val flexibleTasks: List<FlexibleTask>,
    val dayStart: Int = 0,
    val dayEnd: Int = 24 * 60,
    val cancellationIds: Set<String> = emptySet(),
    val locationBufferMinutes: Int = 15,
)

data class DayPlanResult(
    val entries: List<ScheduleEntry>,
    val conflicts: List<ConflictFinding>,
    val changes: List<ChangeRecord>,
    val unscheduled: List<FlexibleTask>,
    val totalFreeMinutes: Int,
    val remainingFreeMinutes: Int,
    val freeRatio: Double,
    val mode: ScheduleMode,
)
