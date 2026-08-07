package com.dewijones92.primavista.database

import androidx.room.TypeConverter
import com.dewijones92.primavista.practice.InputLatency
import com.dewijones92.primavista.score.Polyphony

/**
 * Enum columns are stored by **name**, never by ordinal: reordering a sealed hierarchy or an
 * enum must not change what a stored row means.
 */
public class PrimaVistaConverters {
    @TypeConverter
    public fun fromPolyphony(value: Polyphony): String = value.name

    @TypeConverter
    public fun toPolyphony(value: String): Polyphony = Polyphony.valueOf(value)

    @TypeConverter
    public fun fromProvenance(value: InputLatency.Provenance): String = value.name

    @TypeConverter
    public fun toProvenance(value: String): InputLatency.Provenance = InputLatency.Provenance.valueOf(value)
}
