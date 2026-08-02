package com.smartplanner.core.ai

import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel

/**
 * AI 解析产出（附录 A / 第十六章）。带置信度与待确认问题。
 * 仅含"解析结果"，不直接写入正式日程——须经置信度门控 + 用户确认（附录 D/E）。
 */
data class ParsedItem(
    val type: ItemType,
    val title: String,
    val precision: PrecisionLevel,
    val fixedness: Fixedness,
    val priority: Int,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val weekday: Int? = null,          // 1..7（课程/重复作息）
    val estMinutes: Int? = null,
    val deadlineEpochDay: Long? = null,
    val location: String? = null,
    val confidence: Float,
    val pendingQuestions: List<String> = emptyList(),
)
