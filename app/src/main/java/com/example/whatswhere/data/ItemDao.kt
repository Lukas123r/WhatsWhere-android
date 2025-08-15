package com.example.whatswhere.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM inventory_items WHERE userId = :userId")
    suspend fun deleteAllItemsByUserId(userId: String)

    @Query("SELECT * FROM inventory_items WHERE needsSync = 1")
    suspend fun getUnsyncedItems(): List<Item>

    @Query("SELECT * FROM inventory_items")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM inventory_items WHERE id = :itemId")
    fun getItem(itemId: String): Flow<Item?>

    // --- Verleih-Status aktualisieren ---
    @Query("UPDATE inventory_items SET isLent = :isLent, lentTo = :lentTo, returnDate = :returnDate WHERE id = :itemId")
    suspend fun updateLendingStatus(itemId: String, isLent: Boolean, lentTo: String?, returnDate: Long?)

    // --- Statistiken ---
    @Query("SELECT COUNT(id) FROM inventory_items")
    fun getTotalItemCount(): Flow<Int>

    @Query("SELECT SUM(price) FROM inventory_items")
    fun getTotalItemValue(): Flow<Double?>
}
