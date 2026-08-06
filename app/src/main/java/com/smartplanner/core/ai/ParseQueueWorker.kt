package com.smartplanner.core.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartplanner.core.data.db.SmartDatabase
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.ScheduleItem
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import kotlinx.coroutines.flow.first

/**
 * 离线解析队列处理器（附录 D.4）。
 * 联网后批量消费 pending_parse：调用解析服务 → 按置信度门控 → 写入待确认/正式项。
 */
class ParseQueueWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = SmartDatabase.get(applicationContext)
        val queue = db.pendingParseDao().takeBatch(10)
        if (queue.isEmpty()) return Result.success()

        val parseService = TextParseService()
        val batchDao = db.importBatchDao()
        val itemDao = db.scheduleItemDao()

        for (p in queue) {
            try {
                val parsed = parseService.parse(p.payload, p.kind)
                val batchId = batchDao.insert(
                    ImportBatch(sourceType = p.kind, rawRef = null, parseVersion = "csv-local-1", parsedCount = parsed.size)
                )
                val items = parsed.map { it.toPendingItem(batchId) }
                itemDao.insertAll(items)
                p.id?.let { db.pendingParseDao().delete(it) }
            } catch (e: Exception) {
                p.id?.let { db.pendingParseDao().bumpAttempts(it) }
                if (p.attempts >= 3) p.id?.let { db.pendingParseDao().delete(it) } // 超限丢弃，保留原始可重导
            }
        }
        return Result.success()
    }

    private fun ParsedItem.toPendingItem(batchId: Long): ScheduleItem = ScheduleItem(
        type = type,
        title = title,
        precision = precision,
        fixedness = fixedness,
        priority = priority,
        startMinute = startMinute,
        endMinute = endMinute,
        location = location,
        estMinutes = estMinutes,
        deadlineEpoch = deadlineEpochDay,
        importBatchId = batchId,
        confidence = confidence,
        needsReview = confidence < 0.85f,
        status = ItemStatus.PENDING,
    )

    companion object { const val TAG = "ParseQueueWorker" }
}
