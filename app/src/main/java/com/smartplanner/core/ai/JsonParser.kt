package com.smartplanner.core.ai

import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * JSON 导入解析器（v1.0 扩展导入格式之一）。
 *
 * 使用 Android 自带的 `org.json`，无需引入额外依赖。
 *
 * 支持的输入形态：
 * - JSON 数组：`[ { ... }, { ... } ]`，每个对象解析为一条 [ParsedItem]。
 * - 单个 JSON 对象：`{ ... }`，解析为单条 [ParsedItem]。
 *
 * 每个对象支持的字段（缺省时用合理默认）：
 * - `type`：FIXED / ROUTINE / COURSE / TEMP_ACTIVITY / TODO / GOAL_TASK / REST_BUFFER（不区分大小写，缺省 TODO）
 * - `title`：标题（必填，缺失或为空则跳过该条目）
 * - `start` / `end`：形如 "HH:MM" 或 "HH:mm"，转为当天的分钟数
 * - `weekday`：1..7
 * - `est`：预计时长（分钟数）
 * - `deadline`：形如 "yyyy-MM-dd"，转为 epochDay
 * - `location`：地点
 * - `confidence`：置信度，缺省 1.0；支持数值或字符串形式，自动夹取至 [0,1]
 *
 * fixedness / priority / precision 按类型推导，逻辑对齐 [CsvParser]。
 * 解析失败的条目跳过；整体解析异常返回 emptyList，绝不抛出。
 */
object JsonParser {

    /**
     * 解析 JSON 文本为 [ParsedItem] 列表。
     * 解析失败时返回 emptyList，不抛异常。
     */
    fun parse(text: String): List<ParsedItem> = try {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toParsedList()
        } else {
            listOfNotNull(JSONObject(trimmed).toParsedItem())
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun JSONArray.toParsedList(): List<ParsedItem> =
        (0 until length()).mapNotNull { i ->
            try {
                (get(i) as? JSONObject)?.toParsedItem()
            } catch (_: Exception) {
                null
            }
        }

    private fun JSONObject.toParsedItem(): ParsedItem? = try {
        val type = itemType(optString("type", ""))
        val title = optString("title", "").trim()
        if (title.isEmpty()) return null

        val start = parseHHMM(optString("start", ""))
        val end = parseHHMM(optString("end", ""))
        val weekday = optInt("weekday", 0).takeIf { it in 1..7 }
        val est = optInt("est", 0).takeIf { it > 0 }
        val deadline = parseDate(optString("deadline", ""))
        val location = optString("location", "").trim().takeIf { it.isNotEmpty() }

        ParsedItem(
            type = type,
            title = title,
            precision = precisionFor(type, start, end, deadline),
            fixedness = fixednessFor(type),
            priority = priorityFor(type),
            startMinute = start,
            endMinute = end,
            weekday = weekday,
            estMinutes = est,
            deadlineEpochDay = deadline,
            location = location,
            confidence = parseConfidence(opt("confidence")),
        )
    } catch (_: Exception) {
        null
    }

    /** HH:MM / HH:mm → 当天分钟数；非法返回 null。 */
    private fun parseHHMM(s: String): Int? {
        if (s.isBlank()) return null
        val p = s.split(":")
        if (p.size != 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** "yyyy-MM-dd" → epochDay；非法返回 null。 */
    private fun parseDate(s: String): Long? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim()).toEpochDay()
    } catch (_: Exception) {
        null
    }

    /** 置信度兼容数值/字符串，缺省 1.0，并夹取至 [0,1]。 */
    private fun parseConfidence(v: Any?): Float = when (v) {
        is Number -> v.toFloat()
        is String -> v.trim().toFloatOrNull() ?: 1f
        null -> 1f
        else -> 1f
    }.coerceIn(0f, 1f)

    private fun itemType(s: String): ItemType = when (s.trim().uppercase()) {
        "COURSE" -> ItemType.COURSE
        "ROUTINE" -> ItemType.ROUTINE
        "TEMP_ACTIVITY", "TEMP" -> ItemType.TEMP_ACTIVITY
        "TODO" -> ItemType.TODO
        "FIXED" -> ItemType.FIXED
        "GOAL_TASK" -> ItemType.GOAL_TASK
        "REST_BUFFER", "REST" -> ItemType.REST_BUFFER
        else -> ItemType.TODO
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
