package de.lshorizon.whatswhere.data.dao

import androidx.annotation.Keep
import androidx.room.Entity
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "categories",
    primaryKeys = ["userId", "name"]
)
data class Category(
    val name: String,
    val resourceId: Int = 0,
    val userId: String = "", // empty string denotes app-wide default category
    val isPendingSync: Boolean = false
) {
    // No-arg constructor for Firestore deserialization
    constructor() : this(name = "", resourceId = 0, userId = "", isPendingSync = false)
}
