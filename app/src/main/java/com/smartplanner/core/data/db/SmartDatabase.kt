package com.smartplanner.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem

@Database(
    entities = [
        ScheduleItem::class,
        RoutineRule::class,
        Course::class,
        ImportBatch::class,
        PendingParse::class,
    ],
    version = 1,
    exportSchema = false,
)
@androidx.room.TypeConverters(Converters::class)
abstract class SmartDatabase : RoomDatabase() {
    abstract fun scheduleItemDao(): ScheduleItemDao
    abstract fun routineRuleDao(): RoutineRuleDao
    abstract fun courseDao(): CourseDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun pendingParseDao(): PendingParseDao

    companion object {
        @Volatile
        private var INSTANCE: SmartDatabase? = null

        fun get(context: Context): SmartDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext(),
                SmartDatabase::class.java,
                "smart_planner.db",
            ).build().also { INSTANCE = it }
        }
    }
}
