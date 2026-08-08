package com.smartplanner.core.scheduler

import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.Priority
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.scheduler.model.ChangeRecord
import com.smartplanner.core.scheduler.model.DayPlanInput
import com.smartplanner.core.scheduler.model.DayPlanResult
import com.smartplanner.core.scheduler.model.FlexibleTask
import com.smartplanner.core.scheduler.model.Intensity
import com.smartplanner.core.scheduler.model.ScheduleEntry
import com.smartplanner.core.scheduler.model.TimeBlock

/**
 * 端侧规则引擎（附录 D.1）：确定性、离线、可解释。
 *
 * 处理（对齐 PRD 第八章处理顺序）：
 * 1. 应用取消规则 → 2. 检测冲突 → 3. 临时活动/固定覆盖作息（睡眠/用餐可被覆盖，AI 不压缩睡眠）
 * → 4. 计算空闲 → 5. 放置灵活任务（不入睡眠、保留空闲比例）→ 6. 插入休息 → 7. 产出变更说明。
 *
 * 不自动拆分目标（拆分仅生成 SPLIT_SUGGESTION 建议，须用户确认）。
 */
class ScheduleEngine(
    private val conflictDetector: ConflictDetector = ConflictDetector(),
) {

    fun planDay(
        input: DayPlanInput,
        mode: ScheduleMode = ScheduleMode.BALANCED,
        freeRatioThreshold: Float = 0.20f,
    ): DayPlanResult {
        val changes = mutableListOf<ChangeRecord>()
        val effective = input.anchors.filter { it.id !in input.cancellationIds }

        // 1) 冲突检测（A/B）。类型 C 在放置阶段以"拒绝排入睡眠"实现，不产出冲突。
        val conflicts = conflictDetector.detect(effective, input.locationBufferMinutes)

        // 2) 覆盖作息：用占用方（非 ROUTINE）裁剪作息/睡眠段。睡眠/用餐可被临时活动覆盖，次日恢复。
        val occupying = effective.filter { it.type != ItemType.ROUTINE }
        val routines = effective.filter { it.type == ItemType.ROUTINE }
        val routineRemains = mutableListOf<TimeBlock>()
        for (r in routines) {
            val before = r.endMinute - r.startMinute
            val pieces = RestInserter.subtract(r, occupying)
            val after = pieces.sumOf { it.endMinute - it.startMinute }
            if (after < before) {
                changes += ChangeRecord(
                    changeType = ChangeType.COVERS_ROUTINE,
                    reason = "作息「${r.title}」被覆盖 ${before - after} 分钟，规则次日恢复",
                    affectedSourceIds = listOfNotNull(r.sourceItemId).toSet(),
                )
            }
            routineRemains += pieces
        }

        // 3) 空闲区间 = [dayStart, dayEnd] 减去 (occupying + routineRemains)
        val busy = (occupying + routineRemains).sortedBy { it.startMinute }
        val free = freeIntervals(busy, input.dayStart, input.dayEnd)
        val totalFree = free.sumOf { it.second - it.first }

        // 4) 放置灵活任务
        val placed = mutableListOf<ScheduleEntry>()
        val restEntries = mutableListOf<ScheduleEntry>()
        val unscheduled = mutableListOf<FlexibleTask>()
        val mutableFree = free.map { it.first to it.second }.toMutableList() // (start,end)
        var placedMinutes = 0

        val order = input.flexibleTasks.sortedWith(
            compareByDescending<FlexibleTask> { it.priority }
                .thenBy { it.deadlineEpochDay }
                .thenByDescending { it.estMinutes }
        )

        for (task in order) {
            val slotIdx = mutableFree.indexOfFirst { (it.second - it.first) >= task.estMinutes }
            if (slotIdx < 0) {
                // 无整块空位
                val remaining = totalFree - placedMinutes
                if (task.priority > Priority.ANYTIME && remaining >= task.estMinutes) {
                    changes += ChangeRecord(
                        changeType = ChangeType.SPLIT_SUGGESTION,
                        reason = "「${task.title}」需 ${task.estMinutes}min，无整块空位但总剩余 ${remaining}min 可拆分（须确认）",
                        affectedSourceIds = listOfNotNull(task.sourceItemId).toSet(),
                    )
                }
                if (task.priority > Priority.ANYTIME) {
                    changes += ChangeRecord(
                        changeType = ChangeType.UNSCHEDULED,
                        reason = "「${task.title}」今日无法安排",
                        affectedSourceIds = listOfNotNull(task.sourceItemId).toSet(),
                    )
                }
                unscheduled += task
                continue
            }

            val isAnytime = task.priority <= Priority.ANYTIME
            if (isAnytime) {
                // ANYTIME 仅在保留空闲比例前提下填充
                val afterPlace = totalFree - placedMinutes - task.estMinutes
                if (afterPlace < freeRatioThreshold * totalFree) {
                    unscheduled += task   // 排不下不计逾期、不报冲突
                    continue
                }
            }

            val (fs, fe) = mutableFree[slotIdx]
            val taskEnd = fs + task.estMinutes
            val entry = ScheduleEntry(
                id = task.id,
                title = task.title,
                startMinute = fs,
                endMinute = taskEnd,
                priority = task.priority,
                type = ItemType.GOAL_TASK,
                sourceItemId = task.sourceItemId,
            )
            placed += entry
            placedMinutes += task.estMinutes
            changes += ChangeRecord(
                changeType = ChangeType.TIME_ALLOCATED,
                reason = "「${task.title}」安排至 ${fmt(fs)}–${fmt(taskEnd)}",
                after = "${fmt(fs)}-${fmt(taskEnd)}",
                affectedSourceIds = listOfNotNull(task.sourceItemId).toSet(),
            )

            // 休息插入（同区间内有空位则标记）
            val restMin = RestInserter.restMinutes(task.estMinutes, task.intensity)
            val roomAfter = fe - taskEnd
            val restPlaced = if (restMin in 1..roomAfter) restMin else 0
            if (restPlaced > 0) {
                restEntries += RestInserter.buildRestEntry(entry, restPlaced)
                placedMinutes += restPlaced
                changes += ChangeRecord(
                    changeType = ChangeType.REST_INSERTED,
                    reason = "「${task.title}」后插入 ${restPlaced}min 休息",
                    affectedSourceIds = listOfNotNull(task.sourceItemId).toSet(),
                )
            }

            // 更新空闲区间
            val consumed = task.estMinutes + restPlaced
            val newStart = fs + consumed
            if (newStart < fe) mutableFree[slotIdx] = newStart to fe
            else mutableFree.removeAt(slotIdx)
        }

        // 5) 汇总条目
        val occupyingEntries = occupying.map { it.toEntry() }
        val routineEntries = routineRemains.map { it.toEntry(routine = true) }
        val all = (occupyingEntries + routineEntries + placed + restEntries)
            .sortedBy { it.startMinute }

        val remainingFree = (totalFree - placedMinutes).coerceAtLeast(0)
        val freeRatio = if (totalFree > 0) remainingFree.toDouble() / totalFree else 1.0

        return DayPlanResult(
            entries = all,
            conflicts = conflicts,
            changes = changes,
            unscheduled = unscheduled,
            totalFreeMinutes = totalFree,
            remainingFreeMinutes = remainingFree,
            freeRatio = freeRatio,
            mode = mode,
        )
    }

    /** 计算空闲区间（合并 busy 后取补集）。 */
    private fun freeIntervals(busy: List<TimeBlock>, dayStart: Int, dayEnd: Int): List<Pair<Int, Int>> {
        val merged = mutableListOf<Pair<Int, Int>>()
        for (b in busy) {
            val s = b.startMinute.coerceIn(dayStart, dayEnd)
            val e = b.endMinute.coerceIn(dayStart, dayEnd)
            if (e <= s) continue
            if (merged.isEmpty() || s > merged.last().second) merged += s to e
            else merged[merged.lastIndex] = merged.last().first to maxOf(merged.last().second, e)
        }
        val free = mutableListOf<Pair<Int, Int>>()
        var cur = dayStart
        for (m in merged) {
            if (m.first > cur) free += cur to m.first
            cur = m.second
        }
        if (cur < dayEnd) free += cur to dayEnd
        return free
    }

    private fun TimeBlock.toEntry(routine: Boolean = false) = ScheduleEntry(
        id = id,
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        priority = priority,
        type = type,
        location = location,
        sourceItemId = sourceItemId,
    )

    private fun fmt(min: Int): String {
        val h = (min / 60).toString().padStart(2, '0')
        val m = (min % 60).toString().padStart(2, '0')
        return "$h:$m"
    }
}
