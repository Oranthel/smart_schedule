package com.smartplanner.core.ai

import android.util.Log

/**
 * 图片 OCR 解析器（v1.0 占位实现）。
 *
 * **当前为占位实现**：[parse] 始终返回 emptyList，并输出 Log.w 提示。
 * 真实 OCR 能力计划在 **v1.1** 接入 **ML Kit Text Recognition**（中文 + 拉丁文识别），
 * 现暂不引入 ML Kit 等重型依赖以控制 v1.0 包体与构建复杂度。
 *
 * 后续接入方式（v1.1 规划）：
 * 1. 在 `app/build.gradle` 添加依赖：
 *    ```
 *    implementation 'com.google.mlkit:text-recognition-chinese:16.0.1'
 *    ```
 * 2. 将 [parse] 改为挂起函数或回调形式，内部用
 *    `InputImage.fromFilePath(context, Uri)` + `TextRecognition.getClient(...)` 异步识别。
 * 3. 识别出的文本再走 [TextRuleParser] / LLM 解析为 [ParsedItem]。
 * 4. 注意：OCR 为异步操作，调用方需在协程或主线程回调中处理结果。
 *
 * 接口签名（`parse(imagePath: String): List<ParsedItem>`）当前保留同步形态以便
 * 导入流程统一调度；v1.1 接入时建议升级为 `suspend fun parse(...)` 并由调用方适配。
 */
object ImageOcrParser {

    private const val TAG = "ImageOcrParser"

    /**
     * 解析图片为 [ParsedItem] 列表。
     *
     * v1.0 占位：始终返回 emptyList 并打印警告日志。
     *
     * @param imagePath 图片本地路径（v1.1 接入 ML Kit 后生效）。
     * @return 当前始终为 emptyList。
     */
    fun parse(imagePath: String): List<ParsedItem> {
        Log.w(TAG, "图片 OCR 需接入 ML Kit,当前为占位")
        return emptyList()
    }
}
