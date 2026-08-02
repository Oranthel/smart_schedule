package com.smartplanner.core.ai

/** 解析客户端（附录 D.2）：仅解析，不调度、不直接写入正式日程。 */
interface ParseClient {
    /**
     * @param payload 待解析片段（数据最小化：仅此片段，不含全量历史，附录 D.5）
     * @param kind QUICK_NOTE / IMPORT_TEXT / IMPORT_CSV / IMPORT_OCR
     * @return 结构化结果（带置信度与待确认问题）
     */
    suspend fun parse(payload: String, kind: String): List<ParsedItem>
}
