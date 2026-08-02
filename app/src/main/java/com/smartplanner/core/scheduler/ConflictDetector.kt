package com.smartplanner.core.scheduler

import com.smartplanner.core.data.model.ConflictType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.scheduler.model.ConflictFinding
import com.smartplanner.core.scheduler.model.TimeBlock

/** 区间工具。半开区间 [s, e)。 */
internal object Intervals {
    fun overlap(a: TimeBlock, b: TimeBlock): Boolean =
        a.startMinute < b.endMinute && b.startMinute < a.endMinute

    fun overlapAmount(a: TimeBlock, b: TimeBlock): Int =
        (minOf(a.endMinute, b.endMinute) - maxOf(a.startMinute, b.startMinute)).coerceAtLeast(0)
}

/**
 * 冲突检测（附录 C）。
 * - 类型 A：两个"不可移动"锚点（HARD 或 TEMP_ACTIVITY）时间相交。
 * - 类型 B：相邻锚点地点不同且间隔 < 缓冲。
 * - 类型 C：AI 排入睡眠段——在引擎放置阶段直接拒绝（不产出 ConflictFinding，见附录 C.3）。
 */
class ConflictDetector {

    /** 不可移动 = 硬性固定 或 临时活动（用户指定时间，不可自动移动）。 */
    private fun immovable(b: TimeBlock) =
        b.fixedness == Fixedness.HARD || b.type == ItemType.TEMP_ACTIVITY

    fun detect(anchors: List<TimeBlock>, locationBufferMinutes: Int): List<ConflictFinding> {
        val out = mutableListOf<ConflictFinding>()
        val sorted = anchors.sortedBy { it.startMinute }

        // 类型 A：不可移动锚点两两相交
        for (i in sorted.indices) {
            if (!immovable(sorted[i])) continue
            for (j in (i + 1) until sorted.size) {
                if (!immovable(sorted[j])) continue
                if (Intervals.overlap(sorted[i], sorted[j])) {
                    out += ConflictFinding(
                        type = ConflictType.A_FIXED_OVERLAP,
                        itemIds = setOf(sorted[i].id, sorted[j].id),
                        reason = "固定事项时间相交：${sorted[i].title} 与 ${sorted[j].title}",
                    )
                }
            }
        }

        // 类型 B：相邻不同地点缓冲不足
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (a.location == null || b.location == null) continue
            if (a.location == b.location) continue
            val gap = b.startMinute - a.endMinute
            if (gap in 1 until locationBufferMinutes) {
                out += ConflictFinding(
                    type = ConflictType.B_BUFFER_INSUFFICIENT,
                    itemIds = setOf(a.id, b.id),
                    reason = "转场缓冲不足（${gap}min < ${locationBufferMinutes}min）：${a.title} → ${b.title}",
                )
            }
        }
        return out
    }
}
