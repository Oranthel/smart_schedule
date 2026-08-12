package com.smartplanner.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 番茄钟记录 DAO。
 *
 * 风格对齐项目其他 DAO：观察类查询返回 [Flow]，写操作使用 `suspend`。
 * 实体引用使用全限定名 [com.smartplanner.core.data.entity.PomodoroRecord]，
 * 以避免与未来同名类型产生 import 冲突。
 */
@Dao
interface PomodoroDao {

    @Query("SELECT * FROM pomodoro_record WHERE completedAtDay = :day ORDER BY completedAt ASC")
    fun observeDay(day: Long): Flow<List<com.smartplanner.core.data.entity.PomodoroRecord>>

    @Query("SELECT * FROM pomodoro_record WHERE completedAtDay BETWEEN :startDay AND :endDay ORDER BY completedAt ASC")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<com.smartplanner.core.data.entity.PomodoroRecord>>

    @Query("SELECT COUNT(*) FROM pomodoro_record WHERE completedAtDay = :day AND interrupted = 0")
    fun observeDayCount(day: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM pomodoro_record WHERE completedAtDay BETWEEN :startDay AND :endDay AND interrupted = 0")
    fun observeRangeCount(startDay: Long, endDay: Long): Flow<Int>

    @Insert
    suspend fun insert(record: com.smartplanner.core.data.entity.PomodoroRecord): Long

    @Query("SELECT * FROM pomodoro_record WHERE completedAtDay BETWEEN :startDay AND :endDay AND interrupted = 0 ORDER BY completedAt ASC")
    suspend fun rangeCompleted(startDay: Long, endDay: Long): List<com.smartplanner.core.data.entity.PomodoroRecord>
}
