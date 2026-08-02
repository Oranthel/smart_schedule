package com.smartplanner.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel

/**
 * 统一事项实体（附录 A.1）。
 * 时间表示：
 * - [dateEpoch] = 当天 LocalDate.toEpochDay()；为 null 表示未排定到具体日（待办）。
 * - [startMinute]/[endMinute] = 一天内的分钟数（0..1439）；null 表示全天/未定时。
 */
@Entity(
    tableName = "schedule_items",
    indices = [Index("dateEpoch"), Index("status"), Index("parentId")]
)
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val title: String,
    val precision: PrecisionLevel,
    val fixedness: Fixedness,
    val priority: Int,
    val dateEpoch: Long? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val location: String? = null,
    val estMinutes: Int? = null,
    val spentMinutes: Int = 0,
    val deadlineEpoch: Long? = null,
    val parentId: Long? = null,
    val importBatchId: Long? = null,
    val sourceRef: String? = null,
    val confidence: Float = 1f,
    val needsReview: Boolean = false,
    val status: ItemStatus = ItemStatus.PENDING,
    val intensity: Int = 1,            // 1 普通 2 中 3 高（休息/缓冲计算用）
    val notes: String? = null,
    val allDay: Boolean = false,
)

/** 基础作息规则（附录 A.1）。睡眠/用餐 protected=false，aiNoSchedule=true（仅约束 AI）。 */
@Entity(tableName = "routine_rules")
data class RoutineRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekdays: Set<Int>,           // 1..7（周一..周日）
    val startMinute: Int,
    val endMinute: Int,
    val protectedSegment: Boolean = false,   // v1.1 决策：睡眠/用餐 = false
    val aiNoSchedule: Boolean = false,       // 睡眠段：AI 不得排入灵活任务
    val validFromEpoch: Long? = null,
    val validToEpoch: Long? = null,
    val exceptions: Set<Long> = emptySet(),  // 例外日期 epochDay
    val sourceRef: String? = null,
    val active: Boolean = true,
)

/** 课程（附录 A.1）。默认 hard 固定。 */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekday: Int,                  // 1..7
    val startMinute: Int,
    val endMinute: Int,
    val weekStart: Int,                // 教学周
    val weekEnd: Int,
    val oddEven: OddEven = OddEven.ALL,
    val classroom: String? = null,
    val teacher: String? = null,
    val semesterId: String,
    val archived: Boolean = false,
) {
    enum class OddEven { ALL, ODD, EVEN }
}

/** 宽泛目标（附录 A.1）。 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val estMinutes: Int,
    val spentMinutes: Int = 0,
    val deadlineEpoch: Long? = null,
    val status: ItemStatus = ItemStatus.PENDING,
    val parentId: Long? = null,
)

/** AI 调整变更记录（附录 A.4）。 */
@Entity(tableName = "change_logs")
data class ChangeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val changeType: com.smartplanner.core.data.model.ChangeType,
    val before: String? = null,
    val after: String? = null,
    val reason: String,
    val affectedIds: Set<Long> = emptySet(),
    val confirmed: Boolean = true,
    val reversible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 导入批次（附录 A.1）。确认前不进正式日程。 */
@Entity(tableName = "import_batches")
data class ImportBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,            // TEXT / CSV / IMAGE / PDF
    val rawRef: String? = null,
    val parseVersion: String,
    val parsedCount: Int,
    val confirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 冲突记录（附录 C）。 */
@Entity(tableName = "conflicts")
data class Conflict(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: com.smartplanner.core.data.model.ConflictType,
    val itemIds: Set<Long>,
    val dateEpoch: Long,
    val severity: Int,
    val resolution: String? = null,
    val status: String = "OPEN",       // OPEN / RESOLVED / IGNORED
)

/** 离线解析队列（附录 D.4）。 */
@Entity(tableName = "pending_parse")
data class PendingParse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val kind: String,                  // QUICK_NOTE / IMPORT_TEXT / IMPORT_OCR
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
