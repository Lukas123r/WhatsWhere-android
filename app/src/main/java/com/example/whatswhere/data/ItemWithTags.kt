package com.example.whatswhere.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ItemWithTags(
    @Embedded val item: Item,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ItemTagCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>
)