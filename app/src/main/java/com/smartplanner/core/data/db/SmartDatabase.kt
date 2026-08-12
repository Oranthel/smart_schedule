package com.smartplanner.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.PomodoroRecord
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem

@Database(
    entities = [
        ScheduleItem::class,
        RoutineRule::class,
        Course::class,
        ImportBatch::class,
        PendingParse::class,
        PomodoroRecord::class,
    ],
    version = 2,
    exportSchema = false,
)
@androidx.room.TypeConverters(Converters::class)
abstract class SmartDatabase : RoomDatabase() {
    abstract fun scheduleItemDao(): ScheduleItemDao
    abstract fun routineRuleDao(): RoutineRuleDao
    abstract fun courseDao(): CourseDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun pendingParseDao(): PendingParseDao
    abstract fun pomodoroDao(): PomodoroDao

    companion object {
        @Volatile
        private var INSTANCE: SmartDatabase? = null

        fun get(context: Context): SmartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartDatabase::class.java,
                    "smart_planner.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
