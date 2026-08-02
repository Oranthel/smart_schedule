package com.smartplanner.core.scheduler

import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.ConflictType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.Priority
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.scheduler.model.DayPlanInput
import com.smartplanner.core.scheduler.model.FlexibleTask
import com.smartplanner.core.scheduler.model.Intensity
import com.smartplanner.core.scheduler.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    private val engine = ScheduleEngine()

    private fun block(
        id: String, title: String, s: Int, e: Int,
        type: ItemType = ItemType.COURSE,
        fixedness: Fixedness = Fixedness.HARD,
        priority: Int = Priority.HARD_COURSE_MEETING_DEADLINE,
        location: String? = null,
        aiNoSchedule: Boolean = false,
    ) = TimeBlock(id, title, s, e, priority, fixedness, type, location, aiNoSchedule)

    private fun task(id: String, title: String, est: Int, priority: Int = Priority.FLEXIBLE) =
        FlexibleTask(id, title, est, priority)

    private fun plan(
        anchors: List<TimeBlock>,
        tasks: List<FlexibleTask> = emptyList(),
        dayStart: Int = 480,
        dayEnd: Int = 1440,
        freeRatio: Float = 0.20f,
        cancellations: Set<String> = emptySet(),
    ) = engine.planDay(
        DayPlanInput(
            dateEpochDay = 0,
            anchors = anchors,
            flexibleTasks = tasks,
            dayStart = dayStart,
            dayEnd = dayEnd,
            cancellationIds = cancellations,
        ),
        mode = ScheduleMode.BALANCED,
        freeRatioThreshold = freeRatio,
    )

    // ---------- 冲突 A：两个硬性固定相交 ----------

    @Test fun twoHardCoursesOverlap_producesTypeAConflict() {
        val r = plan(
            anchors = listOf(
                block("c1", "高数", 540, 600),
                block("c2", "英语", 570, 630),
            )
        )
        assertEquals(1, r.conflicts.size)
        assertEquals(ConflictType.A_FIXED_OVERLAP, r.conflicts.first().type)
    }

    // ---------- 临时活动覆盖睡眠：记 COVERS_ROUTINE，非冲突，睡眠被裁剪 ----------

    @Test fun tempActivityCoversSleep_recordsCoverage_notConflict() {
        val sleep = block("sleep", "睡眠", 0, 420,
            type = ItemType.ROUTINE, fixedness = Fixedness.FLEXIBLE,
            priority = Priority.ROUTINE, aiNoSchedule = true)
        val temp = block("t1", "急诊", 60, 120,
            type = ItemType.TEMP_ACTIVITY, fixedness = Fixedness.SOFT,
            priority = Priority.TEMP_ACTIVITY)
        val r = plan(anchors = listOf(sleep, temp), dayStart = 0, dayEnd = 1440)

        // 不是 Type A 冲突（睡眠非不可移动）
        assertTrue(r.conflicts.none { it.type == ConflictType.A_FIXED_OVERLAP })
        // 记录覆盖
        assertTrue(r.changes.any { it.changeType == ChangeType.COVERS_ROUTINE })
        // 睡眠被裁剪为两段，且与临时活动不重叠
        val sleepEntries = r.entries.filter { it.type == ItemType.ROUTINE }
        assertTrue(sleepEntries.isNotEmpty())
        assertTrue(sleepEntries.none { it.startMinute < 120 && it.endMinute > 60 }) // 无段跨越 [60,120)
        // 临时活动条目存在
        assertTrue(r.entries.any { it.id == "t1" && it.startMinute == 60 && it.endMinute == 120 })
    }

    // ---------- 灵活任务不排入睡眠 ----------

    @Test fun flexibleTaskNotPlacedIntoSleep() {
        val sleep = block("sleep", "睡眠", 0, 1380,
            type = ItemType.ROUTINE, fixedness = Fixedness.FLEXIBLE,
            priority = Priority.ROUTINE, aiNoSchedule = true)
        val big = task("f1", "写论文", 120) // 仅剩 1380-1440=60min 空闲，放不下 120
        val r = plan(anchors = listOf(sleep), tasks = listOf(big), dayStart = 0, dayEnd = 1440)

        assertTrue(r.unscheduled.contains(big))
        assertTrue(r.entries.none { it.type == ItemType.GOAL_TASK })
        assertTrue(r.entries.none { it.startMinute < 1380 && it.type == ItemType.GOAL_TASK })
    }

    // ---------- 灵活任务正常排入空闲 ----------

    @Test fun flexibleTaskPlacedInFreeSlot() {
        val course = block("c1", "高数", 540, 600) // 9-10
        val f = task("f1", "复习", 60)
        val r = plan(anchors = listOf(course), tasks = listOf(f), dayStart = 480, dayEnd = 1440)
        val placed = r.entries.first { it.id == "f1" }
        assertEquals(480, placed.startMinute)
        assertEquals(540, placed.endMinute)
    }

    // ---------- 休息插入 ----------

    @Test fun restInsertedAfterLongTask() {
        val f = FlexibleTask("f1", "写论文", 120, Priority.FLEXIBLE, intensity = Intensity.MEDIUM)
        val r = plan(anchors = emptyList(), tasks = listOf(f), dayStart = 480, dayEnd = 1440)
        val rests = r.entries.filter { it.rest }
        assertTrue(rests.isNotEmpty())
        val rest = rests.first()
        assertEquals(600, rest.startMinute) // 任务 480-600，休息紧随
        assertEquals(610, rest.endMinute)   // 120min 中等 → 10min 休息
    }

    // ---------- ANYTIME 受保留空闲比例约束 ----------

    @Test fun anytimeTaskRespectsFreeRatio() {
        // 总空闲 600（480-1080），阈值 0.20 → 须保留 120
        val a1 = FlexibleTask("a1", "阅读", 400, Priority.ANYTIME) // 留 200 ≥120 → 可放
        val a2 = FlexibleTask("a2", "整理", 200, Priority.ANYTIME) // 留 0 <120 → 不放
        val r = plan(anchors = emptyList(), tasks = listOf(a1, a2), dayStart = 480, dayEnd = 1080, freeRatio = 0.20f)
        assertTrue(r.entries.any { it.id == "a1" })
        assertTrue(r.unscheduled.contains(a2))
    }

    // ---------- 无法整块安排 → 拆分建议 + UNSCHEDULED ----------

    @Test fun noSingleSlotButTotalEnough_emitsSplitSuggestion() {
        // 两个空闲块：[480,510]=30 与 [600,660]=60，总 90
        val a1 = block("x1", "块1", 510, 600)
        val a2 = block("x2", "块2", 660, 1440)
        val f = task("f1", "写论文", 75) // 单块最大 60 <75，总 90 ≥75
        val r = plan(anchors = listOf(a1, a2), tasks = listOf(f), dayStart = 480, dayEnd = 1440)
        assertTrue(r.changes.any { it.changeType == ChangeType.SPLIT_SUGGESTION })
        assertTrue(r.changes.any { it.changeType == ChangeType.UNSCHEDULED })
        assertTrue(r.unscheduled.contains(f))
    }

    // ---------- 冲突 B：转场缓冲不足 ----------

    @Test fun differentLocationSmallGap_producesTypeBConflict() {
        val a = block("a", "课A", 540, 600, location = "教1")
        val b = block("b", "课B", 605, 660, location = "教2") // gap=5 <15
        val r = plan(anchors = listOf(a, b))
        assertTrue(r.conflicts.any { it.type == ConflictType.B_BUFFER_INSUFFICIENT })
    }

    // ---------- 取消规则生效 ----------

    @Test fun cancellationRemovesAnchor() {
        val a = block("a", "课A", 540, 600)
        val b = block("b", "课B", 540, 600) // 与 a 相交
        val r = plan(anchors = listOf(a, b), cancellations = setOf("b"))
        // b 被取消，不再触发冲突
        assertTrue(r.conflicts.none { it.type == ConflictType.A_FIXED_OVERLAP })
        assertFalse(r.entries.any { it.id == "b" })
    }

    // ---------- AI 不压缩睡眠：灵活任务不会排入睡眠，freeRatio 合理 ----------

    @Test fun sleepNotCompressedByAiScheduling() {
        val sleep = block("sleep", "睡眠", 0, 480,
            type = ItemType.ROUTINE, fixedness = Fixedness.FLEXIBLE,
            priority = Priority.ROUTINE, aiNoSchedule = true)
        val f = task("f1", "复习", 600) // 一整天 600min 灵活任务
        val r = plan(anchors = listOf(sleep), tasks = listOf(f), dayStart = 0, dayEnd = 1440)
        // 任务只能落在 480-1440（960min），不侵入 0-480 睡眠
        val placed = r.entries.firstOrNull { it.id == "f1" }
        assertTrue(placed != null)
        assertTrue(placed!!.startMinute >= 480)
    }
}
