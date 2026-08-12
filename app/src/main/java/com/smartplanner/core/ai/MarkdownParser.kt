package com.smartplanner.core.ai

import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority
import java.time.LocalDate

/**
 * Markdown 任务列表解析器（v1.0 扩展导入格式之一）。
 *
 * 支持的行级语法：
 * - `- [ ] 标题` 或 `* [ ] 标题` 或 `+ [ ] 标题` → TODO（未完成）
 * - `- [x] 标题` → 仍解析为 TODO（已完成状态不改变类型）
 * - 行尾 ` @2026-08-15` → deadline（"yyyy-MM-dd"）
 * - 行尾 ` 15:00-16:30` → start / end（"HH:MM-HH:MM"）
 * - 行内任意位置 ` #tag` → 忽略（清理掉）
 *
 * 默认类型 TODO，precision ANYTIME，confidence 0.8。
 * 当存在时间或 deadline 时，precision 自动调整为 EXACT / RANGE / DEADLINE。
 *
 * 解析失败的行跳过；整体异常返回 emptyList，绝不抛出。
 */
object MarkdownParser {

    /**
     * 解析 Markdown 文本为 [ParsedItem] 列表。
     * 每行独立解析，无法识别的行跳过。
     */
    fun parse(text: String): List<ParsedItem> {
        if (text.isBlank()) return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseLine)
            .toList()
    }

    /** 任务行前缀：`- [ ]` / `* [x]` / `+ [X]` 等。 */
    private val TASK_PATTERN = Regex("""^[-*+]\s+\[[ xX]\]\s*(.+)$""")

    /** 行尾时间区间：` 15:00-16:30`。 */
    private val TIME_PATTERN = Regex("""\s+(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s*$""")

    /** 行尾 deadline：` @2026-08-15`。 */
    private val DEADLINE_PATTERN = Regex("""\s+@(\d{4}-\d{2}-\d{2})\s*$""")

    /** 任意 ` #tag`，用于清理。 */
    private val TAG_PATTERN = Regex("""\s+#[^\s#]+""")

    private fun parseLine(line: String): ParsedItem? = try {
        val taskMatch = TASK_PATTERN.find(line) ?: return null
        var body = taskMatch.groupValues[1].trim()

        // 1. 提取行尾时间区间
        var start: Int? = null
        var end: Int? = null
        TIME_PATTERN.find(body)?.let { tm ->
            start = parseHHMM(tm.groupValues[1], tm.groupValues[2])
            end = parseHHMM(tm.groupValues[3], tm.groupValues[4])
            body = body.removeSuffix(tm.value).trim()
        }

        // 2. 提取行尾 deadline
        var deadline: Long? = null
        DEADLINE_PATTERN.find(body)?.let { dm ->
            deadline = parseDate(dm.groupValues[1])
            body = body.removeSuffix(dm.value).trim()
        }

        // 3. 清理 #tag
        body = TAG_PATTERN.replace(body, "").trim()
        if (body.isEmpty()) return null

        val type = ItemType.TODO
        ParsedItem(
            type = type,
            title = body,
            precision = precisionFor(type, start, end, deadline),
            fixedness = fixednessFor(type),
            priority = priorityFor(type),
            startMinute = start,
            endMinute = end,
            deadlineEpochDay = deadline,
            confidence = 0.8f,
        )
    } catch (_: Exception) {
        null
    }

    /** HH/MM → 当天分钟数；非法返回 null。 */
    private fun parseHHMM(h: String, m: String): Int? {
        val hh = h.toIntOrNull() ?: return null
        val mm = m.toIntOrNull() ?: return null
        if (hh !in 0..23 || mm !in 0..59) return null
        return hh * 60 + mm
    }

    /** "yyyy-MM-dd" → epochDay；非法返回 null。 */
    private fun parseDate(s: String): Long? = try {
        LocalDate.parse(s.trim()).toEpochDay()
    } catch (_: Exception) {
        null
    }

    private fun precisionFor(
        type: ItemType,
        start: Int?,
        end: Int?,
        deadline: Long?,
    ): PrecisionLevel = when {
        start != null && end != null -> PrecisionLevel.EXACT
        start != null -> PrecisionLevel.RANGE
        deadline != null -> PrecisionLevel.DEADLINE
        type == ItemType.TODO -> PrecisionLevel.ANYTIME
        else -> PrecisionLevel.DATE
    }

    private fun fixednessFor(type: ItemType) = when (type) {
        ItemType.COURSE, ItemType.FIXED -> Fixedness.HARD
        ItemType.TEMP_ACTIVITY -> Fixedness.SOFT
        ItemType.ROUTINE -> Fixedness.FLEXIBLE
        else -> Fixedness.FLEXIBLE
    }

    private fun priorityFor(type: ItemType) = when (type) {
        ItemType.COURSE, ItemType.FIXED -> Priority.HARD_COURSE_MEETING_DEADLINE
        ItemType.TEMP_ACTIVITY -> Priority.TEMP_ACTIVITY
        ItemType.ROUTINE -> Priority.ROUTINE
        else -> Priority.FLEXIBLE
    }
}
