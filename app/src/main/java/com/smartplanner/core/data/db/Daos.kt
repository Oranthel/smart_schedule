package com.smartplanner.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.ScheduleItem
import com.smartplanner.core.data.model.ItemStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleItemDao {

    @Query("SELECT * FROM schedule_item WHERE status != 'CANCELLED' ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_item WHERE scheduleDateEpoch = :date AND status != 'CANCELLED' ORDER BY startMinute ASC")
    fun observeDay(date: Long): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_item WHERE status = 'PENDING' AND importBatchId IS NOT NULL ORDER BY id DESC")
    fun observePending(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_item WHERE importBatchId = :batchId AND status = 'PENDING'")
    suspend fun findByBatch(batchId: Long): List<ScheduleItem>

    @Query("SELECT * FROM schedule_item WHERE id = :id")
    suspend fun findById(id: Long): ScheduleItem?

    @Insert
    suspend fun insert(item: ScheduleItem): Long

    @Insert
    suspend fun insertAll(items: List<ScheduleItem>)

    @Update
    suspend fun update(item: ScheduleItem)

    @Query("UPDATE schedule_item SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ItemStatus)

    @Query("DELETE FROM schedule_item WHERE importBatchId = :batchId AND status = 'PENDING'")
    suspend fun deletePendingByBatch(batchId: Long)

    @Query("DELETE FROM schedule_item WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ImportBatchDao {

    @Query("SELECT * FROM import_batch ORDER BY id DESC")
    fun observeAll(): Flow<List<ImportBatch>>

    @Insert
    suspend fun insert(batch: ImportBatch): Long

    @Query("UPDATE import_batch SET confirmed = 1 WHERE id = :id")
    suspend fun confirm(id: Long)

    @Query("DELETE FROM import_batch WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PendingParseDao {

    @Query("SELECT * FROM pending_parse ORDER BY id ASC")
    fun observeAll(): Flow<List<PendingParse>>

    @Query("SELECT * FROM pending_parse ORDER BY id ASC LIMIT :limit")
    suspend fun takeBatch(limit: Int): List<PendingParse>

    @Insert
    suspend fun insert(pending: PendingParse): Long

    @Query("DELETE FROM pending_parse WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_parse SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)
}

@Dao
interface RoutineRuleDao {

    @Query("SELECT * FROM routine_rule WHERE archived = 0 ORDER BY startMinute ASC")
    fun observeActive(): Flow<List<com.smartplanner.core.data.entity.RoutineRule>>

    @Insert
    suspend fun insert(rule: com.smartplanner.core.data.entity.RoutineRule): Long

    @Query("DELETE FROM routine_rule WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CourseDao {

    @Query("SELECT * FROM course WHERE archived = 0 ORDER BY weekday ASC, startMinute ASC")
    fun observeActive(): Flow<List<com.smartplanner.core.data.entity.Course>>

    @Insert
    suspend fun insert(course: com.smartplanner.core.data.entity.Course): Long

    @Query("UPDATE course SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
