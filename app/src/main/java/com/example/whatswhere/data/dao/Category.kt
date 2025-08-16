package com.example.whatswhere.data.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val resourceId: Int = 0
)