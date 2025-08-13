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

    // --- Category ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    // Hinzugefügt: Methode zum Einfügen einer Liste von Kategorien (nützlich für vordefinierte Kategorien)
    @Insert(onConflict = OnConflictStrategy.IGNORE) // IGNORE, falls sie schon existieren (basierend auf nameKey UNIQUE constraint)
    suspend fun insertCategories(categories: List<Category>)


    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategory(categoryId: Long): Flow<Category?>

    @Query("SELECT * FROM categories WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getCategoryByNameKey(nameKey: String): Category?

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // --- Tag ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Beibehaltung von IGNORE ist hier sinnvoll
    suspend fun insertTag(tag: Tag): Long

    // HINZUGEFÜGT: Methode zum Einfügen einer Liste von Tags
    // Wichtig: onConflict = OnConflictStrategy.IGNORE, um Duplikate basierend auf dem nameKey (wenn UNIQUE) zu vermeiden
    // oder wenn du sicherstellst, dass du nur neue Tags einfügst.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<Tag>)

    @Query("SELECT * FROM tags ORDER BY id ASC") // Geändert: Sortierung nach ID für konsistente Reihenfolge
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getTagByNameKey(nameKey: String): Tag?

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM ItemTagCrossRef)")
    suspend fun deleteOrphanedTags()

    // --- Relationen & Verknüpfungen ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTagCrossRef(crossRef: ItemTagCrossRef)

    // HINZUGEFÜGT: Methode zum Einfügen einer Liste von ItemTagCrossRefs (nützlich beim Speichern eines Items mit mehreren Tags)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTagCrossRefs(crossRefs: List<ItemTagCrossRef>)

    @Query("DELETE FROM ItemTagCrossRef WHERE itemId = :itemId")
    suspend fun deleteTagsByItemId(itemId: String)


    @Transaction
    @Query("SELECT * FROM inventory_items")
    fun getAllItemsWithTags(): Flow<List<ItemWithTags>>

    @Transaction
    @Query("SELECT * FROM inventory_items WHERE id = :itemId")
    fun getItemWithTags(itemId: String): Flow<ItemWithTags>

    // --- Verleih-Status aktualisieren ---
    @Query("UPDATE inventory_items SET isLent = :isLent, lentTo = :lentTo, returnDate = :returnDate WHERE id = :itemId")
    suspend fun updateLendingStatus(itemId: String, isLent: Boolean, lentTo: String?, returnDate: Long?)

    // --- Statistiken ---
    @Query("SELECT COUNT(id) FROM inventory_items")
    fun getTotalItemCount(): Flow<Int>

    @Query("SELECT SUM(price) FROM inventory_items")
    fun getTotalItemValue(): Flow<Double?>
}
