package com.smartplanner.core.data.db

import androidx.room.TypeConverter
import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.ConflictType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel

class Converters {
    @TypeConverter fun itemTypeToString(v: ItemType?): String? = v?.name
    @TypeConverter fun stringToItemType(v: String?): ItemType? = v?.let { ItemType.valueOf(it) }

    @TypeConverter fun precisionToString(v: PrecisionLevel?): String? = v?.name
    @TypeConverter fun stringToPrecision(v: String?): PrecisionLevel? = v?.let { PrecisionLevel.valueOf(it) }

    @TypeConverter fun fixednessToString(v: Fixedness?): String? = v?.name
    @TypeConverter fun stringToFixedness(v: String?): Fixedness? = v?.let { Fixedness.valueOf(it) }

    @TypeConverter fun statusToString(v: ItemStatus?): String? = v?.name
    @TypeConverter fun stringToStatus(v: String?): ItemStatus? = v?.let { ItemStatus.valueOf(it) }

    @TypeConverter fun changeTypeToString(v: ChangeType?): String? = v?.name
    @TypeConverter fun stringToChangeType(v: String?): ChangeType? = v?.let { ChangeType.valueOf(it) }

    @TypeConverter fun conflictTypeToString(v: ConflictType?): String? = v?.name
    @TypeConverter fun stringToConflictType(v: String?): ConflictType? = v?.let { ConflictType.valueOf(it) }

    @TypeConverter fun intSetToString(v: Set<Int>?): String = v?.joinToString(",") ?: ""
    @TypeConverter fun stringToIntSet(v: String?): Set<Int> =
        if (v.isNullOrBlank()) emptySet() else v.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    @TypeConverter fun longSetToString(v: Set<Long>?): String = v?.joinToString(",") ?: ""
    @TypeConverter fun stringToLongSet(v: String?): Set<Long> =
        if (v.isNullOrBlank()) emptySet() else v.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
}
