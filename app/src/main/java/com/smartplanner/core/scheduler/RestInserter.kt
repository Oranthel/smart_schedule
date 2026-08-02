package com.smartplanner.core.scheduler

import com.smartplanner.core.scheduler.model.Intensity
import com.smartplanner.core.scheduler.model.ScheduleEntry
import com.smartplanner.core.scheduler.model.TimeBlock

/**
 * 休息/缓冲插入（PRD 第十章）。
 * - est < 45 → 5min
 * - 45..90 → 10min
 * - >90 且高强度 → 20min
 * - >90 普通 → 10min
 */
object RestInserter {
    fun restMinutes(estMinutes: Int, intensity: Intensity): Int = when {
        estMinutes < 45 -> 5
        estMinutes <= 90 -> 10
        intensity == Intensity.HIGH -> 20
        else -> 10
    }

    /** 在任务条目后尝试插入休息条目；返回 (任务条目, 可休息分钟数)。 */
    fun restAfter(task: ScheduleEntry, availableAfter: Int): Pair<ScheduleEntry, Int> {
        val r = restMinutes(task.endMinute - task.startMinute, Intensity.MEDIUM)
        return task to minOf(r, availableAfter)
    }

    fun buildRestEntry(after: ScheduleEntry, minutes: Int): ScheduleEntry = ScheduleEntry(
        id = "${after.id}#rest",
        title = "休息",
        startMinute = after.endMinute,
        endMinute = after.endMinute + minutes,
        priority = 0,
        type = com.smartplanner.core.data.model.ItemType.REST_BUFFER,
        rest = true,
        sourceItemId = after.sourceItemId,
    )

    /** 区间减法：从 block 中切掉 cutters，返回剩余子段。 */
    fun subtract(block: TimeBlock, cutters: List<TimeBlock>): List<TimeBlock> {
        var pieces = listOf(block)
        for (c in cutters.sortedBy { it.startMinute }) {
            val next = mutableListOf<TimeBlock>()
            for (p in pieces) {
                if (!Intervals.overlap(p, c)) { next += p; continue }
                if (p.startMinute < c.startMinute) next += p.copy(endMinute = c.startMinute)
                if (c.endMinute < p.endMinute) next += p.copy(startMinute = c.endMinute)
            }
            pieces = next
        }
        return pieces.filter { it.endMinute > it.startMinute }
    }
}
