package de.lshorizon.whatswhere.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // Returns categories for the given user, plus defaults (userId = "")
    @Query("SELECT * FROM categories WHERE userId = :userId OR userId = '' ORDER BY name ASC")
    fun getCategoriesForUser(userId: String): Flow<List<Category>>

    // Case-insensitive lookup for duplicates for the given user (also checks defaults)
    @Query("SELECT * FROM categories WHERE (userId = :userId OR userId = '') AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByNameForUser(userId: String, name: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    @Query("DELETE FROM categories WHERE userId = :userId AND LOWER(name) = LOWER(:name)")
    suspend fun deleteUserCategoryByName(userId: String, name: String)
}
