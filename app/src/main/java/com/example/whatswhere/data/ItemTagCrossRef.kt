// Pfad: app/src/main/java/com/example/whatswhere/data/ItemTagCrossRef.kt
package com.example.whatswhere.data

import androidx.room.Entity

@Entity(primaryKeys = ["itemId", "tagId"])
data class ItemTagCrossRef(
    val itemId: String, // GEÄNDERT: von Int auf String
    val tagId: Long
)