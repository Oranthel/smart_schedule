package com.smartplanner.core.ai

import android.util.Log

/**
 * 解析服务实现（附录 D.2）：
 * - CSV：本地确定性解析（CsvParser）。
 * - JSON：本地结构化解析（JsonParser）。
 * - Markdown：本地任务列表解析（MarkdownParser）。
 * - 自然语言：本地正则识别（TextRuleParser），识别率有限；云端 LLM 为后续增强。
 * - 图片 OCR：占位（ImageOcrParser），v1.1 接入 ML Kit。
 *
 * 数据最小化：仅上传 [payload] 片段，不含全量历史日程（附录 D.5）。
 */
class TextParseService : ParseClient {

    override suspend fun parse(payload: String, kind: String): List<ParsedItem> = when (kind) {
        "IMPORT_CSV" -> CsvParser.parse(payload)
        "IMPORT_JSON" -> JsonParser.parse(payload)
        "IMPORT_MD", "IMPORT_MARKDOWN" -> MarkdownParser.parse(payload)
        "IMPORT_OCR", "IMPORT_IMAGE" -> ImageOcrParser.parse(payload)
        "QUICK_NOTE", "IMPORT_TEXT" -> {
            // 优先本地正则识别；识别失败再走云端 LLM（桩）
            val local = TextRuleParser.parse(payload)
            if (local.isNotEmpty()) local else parseWithLlm(payload, kind)
        }
        else -> emptyList()
    }

    /**
     * 云端 LLM 解析。v1.0 桩实现：未配置端点时返回空，调用方应将原文入离线队列（附录 D.4）。
     * TODO: 接入真实 LLM 端点（Retrofit），返回结构化 ParsedItem + 置信度 + 待确认问题。
     */
    private suspend fun parseWithLlm(payload: String, kind: String): List<ParsedItem> {
        Log.i("TextParseService", "LLM parse 桩：kind=$kind, len=${payload.length}（未配置端点，返回空）")
        // 真实实现：val resp = api.parse(ParseRequest(payload, kind)); return resp.items
        return emptyList()
    }
}
