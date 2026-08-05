package com.smartplanner.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel

/**
 * 事项实体（附录 A.1）。
 * 统一抽象：课程/固定/临时活动/作息/代办/目标拆分/休息缓冲。
 * [scheduleDateEpoch]：该事项被安排到的日期（epoch day）；
 *   对重复事项为关联日期，对固定/临时活动为实际日期，对宽泛目标为目标起始日期。
 */
@Entity(
    tableName = "schedule_item",
    indices = [
        Index(value = ["scheduleDateEpoch", "status"]),
        Index(value = ["importBatchId"]),
    ],
)
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val title: String,
    val precision: PrecisionLevel = PrecisionLevel.EXACT,
    val fixedness: Fixedness = Fixedness.FLEXIBLE,
    val priority: Int = 30,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val location: String? = null,
    val estMinutes: Int? = null,
    val deadlineEpoch: Long? = null,
    val scheduleDateEpoch: Long? = null,
    val importBatchId: Long? = null,
    val confidence: Float? = null,
    val needsReview: Boolean = false,
    val status: ItemStatus = ItemStatus.PENDING,
    val aiNoSchedule: Boolean = false,
    val dayOfWeek: Int? = null,
)

@Entity(tableName = "routine_rule")
data class RoutineRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekdays: String = "1,2,3,4,5,6,7",
    val startMinute: Int,
    val endMinute: Int,
    val aiNoSchedule: Boolean = false,
    val archived: Boolean = false,
)

@Entity(tableName = "course")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val classroom: String? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "import_batch")
data class ImportBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String = "IMPORT_CSV",
    val rawRef: String? = null,
    val parseVersion: String = "",
    val parsedCount: Int = 0,
    val confirmed: Boolean = false,
    val createdAt: String = "",
)

@Entity(tableName = "pending_parse")
data class PendingParse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val kind: String,
    val attempts: Int = 0,
)
