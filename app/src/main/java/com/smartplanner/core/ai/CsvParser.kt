package com.smartplanner.core.ai

import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority

/**
 * 确定性 CSV 解析（v1.0 导入主路径，无需 LLM）。
 * 表头（不区分大小写，可省略）：type,title,start,end,weekday,est,deadline,location,confidence
 * - start/end 形如 HH:MM；weekday 1..7；est 分钟数；deadline ISO 日期(yyyy-MM-dd)。
 * 缺省字段用合理默认；置信度缺省 1.0。
 */
object CsvParser {

    fun parse(text: String): List<ParsedItem> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val hasHeader = header.any { it in HEADER_KEYS }
        val dataLines = if (hasHeader) lines.drop(1) else lines
        val idx = if (hasHeader) indexMap(header) else DEFAULT_INDEX

        return dataLines.mapNotNull { row ->
            val cols = row.split(",").map { it.trim() }
            try {
                val type = itemType(cols.getOrNull(idx.getValue("type")))
                ParsedItem(
                    type = type,
                    title = cols.getOrNull(idx.getValue("title")) ?: return@mapNotNull null,
                    precision = precisionFor(type, cols, idx),
                    fixedness = fixednessFor(type),
                    priority = priorityFor(type),
                    startMinute = cols.minOrNull(idx.getValue("start")),
                    endMinute = cols.minOrNull(idx.getValue("end")),
                    weekday = cols.intOrNull(idx.getValue("weekday")),
                    estMinutes = cols.intOrNull(idx.getValue("est")),
                    deadlineEpochDay = cols.dateOrNull(idx.getValue("deadline")),
                    location = cols.getOrNull(idx.getValue("location"))?.takeIf { it.isNotBlank() },
                    confidence = cols.getOrNull(idx.getValue("confidence"))?.toFloatOrNull() ?: 1f,
                )
            } catch (_: Exception) { null }
        }
    }

    private val HEADER_KEYS = setOf("type", "title", "start", "end", "weekday", "est", "deadline", "location", "confidence")
    private val DEFAULT_INDEX = mapOf(
        "type" to 0, "title" to 1, "start" to 2, "end" to 3,
        "weekday" to 4, "est" to 5, "deadline" to 6, "location" to 7, "confidence" to 8,
    )

    private fun indexMap(header: List<String>): Map<String, Int> {
        val m = DEFAULT_INDEX.toMutableMap()
        header.forEachIndexed { i, h -> m[h] = i }
        return m
    }

    private fun List<String>.minOrNull(i: Int): Int? = getOrNull(i)?.let(::parseHHMM)
    private fun List<String>.intOrNull(i: Int): Int? = getOrNull(i)?.toIntOrNull()
    private fun List<String>.dateOrNull(i: Int): Long? = getOrNull(i)?.let(::parseDate)

    private fun parseHHMM(s: String): Int? {
        val p = s.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun parseDate(s: String): Long? = try {
        java.time.LocalDate.parse(s.trim()).toEpochDay()
    } catch (_: Exception) { null }

    private fun itemType(s: String?): ItemType = when (s?.trim()?.uppercase()) {
        "COURSE" -> ItemType.COURSE
        "ROUTINE" -> ItemType.ROUTINE
        "TEMP_ACTIVITY", "TEMP" -> ItemType.TEMP_ACTIVITY
        "TODO" -> ItemType.TODO
        "FIXED" -> ItemType.FIXED
        else -> ItemType.TODO
    }

    private fun precisionFor(type: ItemType, cols: List<String>, idx: Map<String, Int>): PrecisionLevel =
        when {
            cols.minOrNull(idx.getValue("start")) != null && cols.minOrNull(idx.getValue("end")) != null -> PrecisionLevel.EXACT
            cols.minOrNull(idx.getValue("start")) != null -> PrecisionLevel.RANGE
            cols.dateOrNull(idx.getValue("deadline")) != null -> PrecisionLevel.DEADLINE
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
