package com.smartplanner.core.data.model

/**
 * 事项类型（PRD 第四章）。 */
enum class ItemType {
    FIXED, ROUTINE, COURSE, TEMP_ACTIVITY, TODO, GOAL_TASK, REST_BUFFER
}

/** 精度级别（PRD 第十二章 / 附录 A.3）。 */
enum class PrecisionLevel {
    EXACT, RANGE, PART_OF_DAY, DATE, WEEK, MONTH, DEADLINE, ANYTIME
}

/** 固定程度（附录 A.3）。HARD 不可自动移动。 */
enum class Fixedness { HARD, SOFT, FLEXIBLE, OPTIONAL }

/** 事项状态（附录 A.3）。 */
enum class ItemStatus {
    PENDING, SCHEDULED, IN_PROGRESS, DONE, SKIPPED, CANCELLED, OVERDUE, UNSCHEDULED
}

/** 调度模式（PRD 第九章）。 */
enum class ScheduleMode { CONSERVATIVE, BALANCED, ACTIVE }

/** 冲突类型（附录 C）。 */
enum class ConflictType { A_FIXED_OVERLAP, B_BUFFER_INSUFFICIENT, C_PROTECTED_VIOLATION }

/** 变更类型（附录 A.4 / 第九章 ChangeLog）。 */
enum class ChangeType {
    COVERS_ROUTINE,      // 临时活动覆盖睡眠/用餐
    MOVED_FLEXIBLE,      // 灵活任务移动
    TIME_ALLOCATED,      // 目标剩余工时分配
    SPLIT_SUGGESTION,    // 拆分建议（须确认）
    REST_INSERTED,       // 插入休息
    UNSCHEDULED,         // 无法安排
    STATUS_CHANGED       // 完成/跳过/延后
}

/**
 * 优先级数值（附录 A.3，高→低）。
 * 取消/调课 100 > 硬性固定 90 > 临时活动 70 > 基础作息 50 > 灵活 30 > 可选 10。
 */
object Priority {
    const val CANCEL_RULE = 100
    // 硬性固定细分
    const val HARD_EXAM_TRANSPORT = 95
    const val HARD_MEDICAL = 93
    const val HARD_COURSE_MEETING_DEADLINE = 91
    const val HARD_USER_LOCKED = 90
    const val TEMP_ACTIVITY = 70
    const val ROUTINE = 50   // 含睡眠/用餐（protected=false）
    const val FLEXIBLE = 30
    const val ANYTIME = 10

    fun hardFor(type: ItemType): Int = when (type) {
        ItemType.COURSE, ItemType.FIXED -> HARD_COURSE_MEETING_DEADLINE
        else -> HARD_COURSE_MEETING_DEADLINE
    }
}
