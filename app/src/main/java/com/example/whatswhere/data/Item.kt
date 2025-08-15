package com.example.whatswhere.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "inventory_items")
data class Item(
    @PrimaryKey @DocumentId
    var id: String = "",
    val userId: String = "",
    val name: String = "",
    val location: String = "",
    val description: String? = null,
    val imagePath: String? = null,
    val quantity: Int = 1,
    val purchaseDate: Long? = null,
    val price: Double? = null,
    val warrantyExpiration: Long? = null,
    val serialNumber: String? = null,
    val modelNumber: String? = null,
    val createdAt: Long = 0,
    val isLent: Boolean = false,
    val lentTo: String? = null,
    val returnDate: Long? = null,
    // NEUES FELD, um den Sync-Status zu verfolgen
    var needsSync: Boolean = false
) : Parcelable