package com.smartplanner.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartplanner.core.data.entity.ChangeLog
import com.smartplanner.core.data.entity.Conflict
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.Goal
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleItemDao {
    @Query("SELECT * FROM schedule_items WHERE dateEpoch = :dateEpoch ORDER BY startMinute IS NULL, startMinute ASC")
    fun observeDay(dateEpoch: Long): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE dateEpoch BETWEEN :from AND :to ORDER BY dateEpoch, startMinute")
    fun observeRange(from: Long, to: Long): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE status = :status")
    fun observeByStatus(status: com.smartplanner.core.data.model.ItemStatus): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE id = :id")
    suspend fun getById(id: Long): ScheduleItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScheduleItem>): List<Long>

    @Update
    suspend fun update(item: ScheduleItem)

    @Delete
    suspend fun delete(item: ScheduleItem)

    @Query("UPDATE schedule_items SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: com.smartplanner.core.data.model.ItemStatus)

    @Query("DELETE FROM schedule_items WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: Long)
}

@Dao
interface RoutineRuleDao {
    @Query("SELECT * FROM routine_rules WHERE active = 1 ORDER BY startMinute")
    fun observeActive(): Flow<List<RoutineRule>>

    @Query("SELECT * FROM routine_rules WHERE active = 1")
    suspend fun getActive(): List<RoutineRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(r: RoutineRule): Long
    @Update suspend fun update(r: RoutineRule)
    @Delete suspend fun delete(r: RoutineRule)
    @Query("SELECT * FROM routine_rules WHERE id = :id")
    suspend fun getById(id: Long): RoutineRule?
    @Query("DELETE FROM routine_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE archived = 0 ORDER BY weekday, startMinute")
    fun observeActive(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE archived = 0")
    suspend fun getActive(): List<Course>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(c: Course): Long
    @Update suspend fun update(c: Course)
    @Query("UPDATE courses SET archived = 1 WHERE semesterId = :semesterId")
    suspend fun archiveSemester(semesterId: String)
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: Long): Course?
    @Query("UPDATE courses SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY endEpoch")
    fun observeAll(): Flow<List<Goal>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(g: Goal): Long
    @Update suspend fun update(g: Goal)
    @Delete suspend fun delete(g: Goal)
}

@Dao
interface ChangeLogDao {
    @Query("SELECT * FROM change_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ChangeLog>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(log: ChangeLog): Long
    @Query("DELETE FROM change_logs WHERE createdAt < :before")
    suspend fun trimOlderThan(before: Long)
}

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportBatch>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(b: ImportBatch): Long
    @Update suspend fun update(b: ImportBatch)
}

@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflicts WHERE status = 'OPEN' ORDER BY dateEpoch")
    fun observeOpen(): Flow<List<Conflict>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(c: Conflict): Long
    @Query("UPDATE conflicts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)
}

@Dao
interface PendingParseDao {
    @Query("SELECT * FROM pending_parse ORDER BY createdAt")
    fun observeAll(): Flow<List<PendingParse>>
    @Query("SELECT * FROM pending_parse ORDER BY createdAt LIMIT :batch")
    suspend fun takeBatch(batch: Int = 10): List<PendingParse>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: PendingParse): Long
    @Delete suspend fun delete(p: PendingParse)
    @Query("UPDATE pending_parse SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)
}
