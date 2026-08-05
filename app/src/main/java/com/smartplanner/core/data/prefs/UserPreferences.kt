package com.smartplanner.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartplanner.core.data.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smart_preferences")

class UserPreferences(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("schedule_mode")
        val FREE_RATIO = floatPreferencesKey("free_ratio")
        val DND_START = intPreferencesKey("dnd_start")
        val DND_END = intPreferencesKey("dnd_end")
        val NIGHT_INTERRUPT = intPreferencesKey("night_interrupt")
        val CONF_LOW = floatPreferencesKey("confidence_low")
        val CONF_HIGH = floatPreferencesKey("confidence_high")
    }

    val scheduleMode: Flow<ScheduleMode> = context.dataStore.data.map { prefs ->
        runCatching { ScheduleMode.valueOf(prefs[Keys.MODE] ?: ScheduleMode.BALANCED.name) }
            .getOrDefault(ScheduleMode.BALANCED)
    }

    val freeRatio: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.FREE_RATIO] ?: 0.20f
    }

    val dndStart: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DND_START] ?: (22 * 60)
    }

    val dndEnd: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DND_END] ?: (7 * 60)
    }

    val nightInterrupt: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[Keys.NIGHT_INTERRUPT] ?: 0) == 1
    }

    val confidenceLow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONF_LOW] ?: 0.70f
    }

    val confidenceHigh: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONF_HIGH] ?: 0.85f
    }

    suspend fun setMode(mode: ScheduleMode) = context.dataStore.edit { it[Keys.MODE] = mode.name }
    suspend fun setFreeRatio(v: Float) = context.dataStore.edit { it[Keys.FREE_RATIO] = v }
    suspend fun setDnd(start: Int, end: Int) = context.dataStore.edit {
        it[Keys.DND_START] = start; it[Keys.DND_END] = end
    }
    suspend fun setNightInterrupt(v: Boolean) = context.dataStore.edit { it[Keys.NIGHT_INTERRUPT] = if (v) 1 else 0 }
    suspend fun setConfidence(low: Float, high: Float) = context.dataStore.edit {
        it[Keys.CONF_LOW] = low; it[Keys.CONF_HIGH] = high
    }
}
