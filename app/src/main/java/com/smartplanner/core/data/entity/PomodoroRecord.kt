package com.smartplanner.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 番茄钟记录实体。
 *
 * 每完成一次（或中断一次）专注即写入一条记录，用于按日/按周统计专注量。
 * - [completedAt]：完成时间（epoch millis），便于精确排序与展示。
 * - [completedAtDay]：完成日期（epoch day），建立索引以便按日高效查询。
 * - [interrupted]：true 表示专注被中断（未完整完成），统计完成数时会被排除。
 */
@Entity(tableName = "pomodoro_record", indices = [Index("completedAtDay")])
data class PomodoroRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceItemId: Long?,           // 关联事项 id（可空）
    val title: String,                 // 任务标题快照
    val durationMinutes: Int,          // 实际专注时长（分钟）
    val completedAt: Long,             // 完成时间 epoch millis
    val completedAtDay: Long,          // 完成日期 epoch day（便于按日查询）
    val interrupted: Boolean = false,  // 是否中断（未完成）
)
