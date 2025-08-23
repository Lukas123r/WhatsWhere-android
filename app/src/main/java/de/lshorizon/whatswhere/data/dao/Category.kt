package de.lshorizon.whatswhere.data.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
// Firestore expects a public no-arg constructor and mutable fields
data class Category @JvmOverloads constructor(
    @PrimaryKey var name: String = "",
    var resourceId: Int = 0
)
