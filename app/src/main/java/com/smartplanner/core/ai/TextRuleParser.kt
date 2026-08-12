package com.smartplanner.core.ai

import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority
import java.time.LocalDate

/**
 * 本地规则解析器（v1.0 扩展导入格式之一，非 LLM）。
 *
 * 通过正则识别自然语言中的常见模式，覆盖率有限但无需联网/模型：
 * - 日期关键词：`今天 / 明天 / 后天`（含今日 / 明日 / today / tomorrow）→ deadlineEpochDay 用相对今天偏移
 * - 时间：`HH点 / H点 / HH点MM分 / HH:mm / H:mm` → startMinute
 * - 时长：`X小时 / X分钟 / Xh / Xmin`（支持小数，如 1.5小时）→ estMinutes
 * - 类型关键词：`开会 / 会议` → FIXED；`上课 / 课程` → COURSE；`提醒 / 记得` → TODO；其余默认 TODO
 * - 标题：去除时间 / 时长 / 日期关键词后的剩余文本
 *
 * 每行独立解析，支持多行；confidence 固定 0.6（本地解析不确定性较高）。
 * 正则无法识别的行跳过，绝不崩溃。
 */
object TextRuleParser {

    /**
     * 解析自然语言文本为 [ParsedItem] 列表。
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

    /** 今日 0 点对应的 epochDay（UTC 天数，与 [ParsedItem.deadlineEpochDay] 口径一致）。 */
    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    /** 日期关键词 → 相对今天的天数偏移。 */
    private val DATE_OFFSET: List<Pair<String, Int>> = listOf(
        "今天" to 0, "今日" to 0, "today" to 0,
        "明天" to 1, "明日" to 1, "tomorrow" to 1,
        "后天" to 2,
    )

    /** 时间：HH:mm 形式 或 H点 / HH点 / HH点MM(分) 形式。 */
    private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})|(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分?)?""")

    /** 时长：数值 + 单位（小时/分钟/h/min），支持小数。 */
    private val DURATION_PATTERN = Regex("""(\d+(?:\.\d+)?)\s*(小时|分钟|分|min|h|H)""")

    private val KEYWORD_FIXED = listOf("开会", "会议", "meeting")
    private val KEYWORD_COURSE = listOf("上课", "课程", "course", "lecture")
    private val KEYWORD_TODO = listOf("提醒", "记得", "todo", "待办")

    private fun parseLine(line: String): ParsedItem? {
        return try {
            if (line.isBlank()) return null

            var working = line

            // 1. 识别日期关键词 → 天数偏移
            var dayOffset = 0
            var hasDate = false
            for ((kw, off) in DATE_OFFSET) {
                if (kw in working) {
                    dayOffset = off
                    hasDate = true
                    working = working.replace(kw, " ")
                    break
                }
            }

            // 2. 识别时间 → startMinute
            var startMinute: Int? = null
            TIME_PATTERN.find(working)?.let { tm ->
                startMinute = parseTimeMatch(tm)
                if (startMinute != null) {
                    working = working.replace(tm.value, " ")
                }
            }

            // 3. 识别时长 → estMinutes
            var estMinutes: Int? = null
            DURATION_PATTERN.find(working)?.let { dm ->
                estMinutes = parseDuration(dm)
                if (estMinutes != null) {
                    working = working.replace(dm.value, " ")
                }
            }

            // 4. 类型识别
            val type = inferType(working)

            // 5. 标题 = 清理后剩余文本（折叠多余空白）
            val title = working.replace(Regex("""\s+"""), " ").trim(' ', '·', '-', '*')
            if (title.isEmpty()) return null

            val deadline = if (hasDate) todayEpochDay() + dayOffset else null

            ParsedItem(
                type = type,
                title = title,
                precision = precisionFor(type, startMinute, deadline),
                fixedness = fixednessFor(type),
                priority = priorityFor(type),
                startMinute = startMinute,
                estMinutes = estMinutes,
                deadlineEpochDay = deadline,
                confidence = 0.6f,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 将时间正则匹配结果转为当天分钟数；非法返回 null。 */
    private fun parseTimeMatch(m: MatchResult): Int? {
        val g = m.groupValues
        return try {
            if (g[1].isNotEmpty()) {
                // HH:MM 形式
                val h = g[1].toIntOrNull() ?: return null
                val mm = g[2].toIntOrNull() ?: return null
                if (h !in 0..23 || mm !in 0..59) return null
                h * 60 + mm
            } else {
                // H点 / HH点 / HH点MM(分) 形式
                val h = g[3].toIntOrNull() ?: return null
                val mm = g[4].ifBlank { "0" }.toIntOrNull() ?: 0
                if (h !in 0..23 || mm !in 0..59) return null
                h * 60 + mm
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 将时长正则匹配结果转为分钟数；非法或非正返回 null。 */
    private fun parseDuration(m: MatchResult): Int? {
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2]
        val minutes = when (unit) {
            "小时", "h", "H" -> (amount * 60).toInt()
            "分钟", "分", "min" -> amount.toInt()
            else -> return null
        }
        return minutes.takeIf { it > 0 }
    }

    /** 根据关键词推断事项类型，默认 TODO。 */
    private fun inferType(text: String): ItemType {
        val lower = text.lowercase()
        return when {
            KEYWORD_FIXED.any { it in lower } -> ItemType.FIXED
            KEYWORD_COURSE.any { it in lower } -> ItemType.COURSE
            KEYWORD_TODO.any { it in lower } -> ItemType.TODO
            else -> ItemType.TODO
        }
    }

    private fun precisionFor(
        type: ItemType,
        start: Int?,
        deadline: Long?,
    ): PrecisionLevel = when {
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
