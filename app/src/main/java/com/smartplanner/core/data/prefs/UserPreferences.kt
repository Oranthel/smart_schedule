package com.smartplanner.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartplanner.core.data.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smart_planner_prefs")

/** 用户偏好（附录 B / D）。所有阈值可配，含默认值。 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val MODE = intPreferencesKey("schedule_mode")
        val FREE_RATIO = floatPreferencesKey("free_ratio")          // 保留空闲比例，默认 0.20
        val DND_START = intPreferencesKey("dnd_start")              // 勿扰起始分钟（默认 22*60）
        val DND_END = intPreferencesKey("dnd_end")                  // 勿扰结束分钟（默认 7*60）
        val CONF_LOW = floatPreferencesKey("conf_low")              // 0.70
        val CONF_HIGH = floatPreferencesKey("conf_high")            // 0.85
        val NIGHT_INTERRUPT = booleanPreferencesKey("night_interrupt") // 夜间闹钟级打断总开关
    }

    val mode: Flow<ScheduleMode> = context.dataStore.data.map {
        ScheduleMode.entries.getOrElse(it[Keys.MODE] ?: 1) { ScheduleMode.BALANCED }
    }
    val freeRatio: Flow<Float> = context.dataStore.data.map { it[Keys.FREE_RATIO] ?: 0.20f }
    val dndStart: Flow<Int> = context.dataStore.data.map { it[Keys.DND_START] ?: (22 * 60) }
    val dndEnd: Flow<Int> = context.dataStore.data.map { it[Keys.DND_END] ?: (7 * 60) }
    val confidenceLow: Flow<Float> = context.dataStore.data.map { it[Keys.CONF_LOW] ?: 0.70f }
    val confidenceHigh: Flow<Float> = context.dataStore.data.map { it[Keys.CONF_HIGH] ?: 0.85f }
    val nightInterrupt: Flow<Boolean> = context.dataStore.data.map { it[Keys.NIGHT_INTERRUPT] ?: false }

    suspend fun setMode(m: ScheduleMode) = context.dataStore.edit { it[Keys.MODE] = m.ordinal }
    suspend fun setFreeRatio(v: Float) = context.dataStore.edit { it[Keys.FREE_RATIO] = v }
    suspend fun setDnd(start: Int, end: Int) = context.dataStore.edit {
        it[Keys.DND_START] = start; it[Keys.DND_END] = end
    }
    suspend fun setConfidence(low: Float, high: Float) = context.dataStore.edit {
        it[Keys.CONF_LOW] = low; it[Keys.CONF_HIGH] = high
    }
    suspend fun setNightInterrupt(v: Boolean) = context.dataStore.edit { it[Keys.NIGHT_INTERRUPT] = v }
}
