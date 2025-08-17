package de.lshorizon.whatswhere.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        // Trennt den String bei jedem Komma und erstellt eine Liste
        // Leere Einträge werden herausgefiltert
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        // Verbindet alle Elemente der Liste mit einem Komma zu einem einzigen String
        return list.joinToString(",")
    }
}
