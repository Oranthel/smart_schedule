package com.smartplanner.core.pomodoro

import com.smartplanner.core.data.db.PomodoroDao
import com.smartplanner.core.data.entity.PomodoroRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 番茄钟记录仓库：负责持久化与查询 [PomodoroRecord]。
 *
 * 查询方法透传 DAO 的 [Flow]；写操作 [recordCompletion] 切换到 IO 线程执行。
 */
class PomodoroRepository(private val dao: PomodoroDao) {

    /** 观察指定日期（epoch day）的全部番茄钟记录。 */
    fun observeDay(day: Long): Flow<List<PomodoroRecord>> = dao.observeDay(day)

    /** 观察指定日期的已完成（非中断）番茄钟数量。 */
    fun observeDayCount(day: Long): Flow<Int> = dao.observeDayCount(day)

    /** 观察一段日期范围（如一周）内已完成的番茄钟数量。 */
    fun observeWeekCount(startDay: Long, endDay: Long): Flow<Int> = dao.observeRangeCount(startDay, endDay)

    /**
     * 记录一次番茄钟完成（或中断）。
     *
     * 自动填充完成时间：[PomodoroRecord.completedAt] 取当前 epoch millis，
     * [PomodoroRecord.completedAtDay] 取今天对应的 epoch day。
     *
     * @param sourceItemId 关联事项 id，可空（手动专注无关联）
     * @param title 任务标题快照
     * @param durationMinutes 实际专注时长（分钟）
     * @param interrupted 是否中断（true 表示未完整完成）
     */
    suspend fun recordCompletion(
        sourceItemId: Long?,
        title: String,
        durationMinutes: Int,
        interrupted: Boolean,
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            PomodoroRecord(
                sourceItemId = sourceItemId,
                title = title,
                durationMinutes = durationMinutes,
                completedAt = System.currentTimeMillis(),
                completedAtDay = LocalDate.now().toEpochDay(),
                interrupted = interrupted,
            ),
        )
    }
}
