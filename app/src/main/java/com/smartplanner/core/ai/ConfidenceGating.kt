package com.smartplanner.core.ai

/** 置信度门控决策（附录 E）。 */
enum class GatingDecision { WRITE, WRITE_REVIEW, PENDING }

object ConfidenceGating {
    /** `>= high` 直接写入；`>= low` 写入但待复核；否则进待确认。 */
    fun decide(confidence: Float, low: Float, high: Float): GatingDecision = when {
        confidence >= high -> GatingDecision.WRITE
        confidence >= low -> GatingDecision.WRITE_REVIEW
        else -> GatingDecision.PENDING
    }

    /** 含待确认问题的一律进待确认，无论置信度。 */
    fun decide(item: ParsedItem, low: Float, high: Float): GatingDecision =
        if (item.pendingQuestions.isNotEmpty()) GatingDecision.PENDING
        else decide(item.confidence, low, high)
}
