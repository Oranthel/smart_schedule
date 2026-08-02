package com.smartplanner.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smartplanner.core.data.entity.ChangeLog
import com.smartplanner.core.data.entity.Conflict
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.Goal
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem

@Database(
    entities = [
        ScheduleItem::class, RoutineRule::class, Course::class, Goal::class,
        ChangeLog::class, ImportBatch::class, Conflict::class, PendingParse::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SmartDatabase : RoomDatabase() {
    abstract fun scheduleItemDao(): ScheduleItemDao
    abstract fun routineRuleDao(): RoutineRuleDao
    abstract fun courseDao(): CourseDao
    abstract fun goalDao(): GoalDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun conflictDao(): ConflictDao
    abstract fun pendingParseDao(): PendingParseDao

    companion object {
        @Volatile private var INSTANCE: SmartDatabase? = null
        fun get(context: Context): SmartDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, SmartDatabase::class.java, "smart_planner.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
