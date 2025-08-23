package de.lshorizon.whatswhere.data

import de.lshorizon.whatswhere.data.dao.CategoryDao
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import de.lshorizon.whatswhere.R

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class CategoryRepository(private val categoryDao: CategoryDao) {

    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    fun getCategories(): Flow<List<Category>> = categoryDao.getCategories()

    suspend fun syncCategories() {
        val userId = currentUserId ?: return
        try {
            // 1. Sync pending local categories to Firebase
            val pendingSyncCategories = categoryDao.getCategories().first().filter { it.isPendingSync }
            pendingSyncCategories.forEach { category ->
                try {
                    FirestoreManager.saveCategory(userId, category)
                    // If successful, update local category to not be pending sync
                    categoryDao.insert(category.copy(isPendingSync = false))
                } catch (e: Exception) {
                    // Log error but don't stop sync, category remains pending
                    e.printStackTrace()
                }
            }

            // 2. Now proceed with the existing sync logic (Firebase to local, and local-only to Firebase)
            val localCategories = categoryDao.getCategories().first() // Re-fetch after pending sync
            val firebaseCategories = FirestoreManager.getCategories(userId)

            // Identify local-only categories (that are not pending sync anymore) and push them to Firebase
            val localOnlyCategories = localCategories.filter { localCategory ->
                !localCategory.isPendingSync && firebaseCategories.none { it.name == localCategory.name }
            }
            localOnlyCategories.forEach { localCategory ->
                FirestoreManager.saveCategory(userId, localCategory)
            }

            val predefinedCategoryNames = mapOf(
                "all" to R.string.category_all,
                "documents" to R.string.category_documents,
                "electronics" to R.string.category_electronics,
                "household" to R.string.category_household,
                "miscellaneous" to R.string.category_miscellaneous,
                "office" to R.string.category_office,
                "tools" to R.string.category_tools
            )

            val categoriesToInsert = firebaseCategories.map { firebaseCategory ->
                val resourceId = predefinedCategoryNames[firebaseCategory.name.lowercase()] ?: 0
                firebaseCategory.copy(resourceId = resourceId)
            }

            categoryDao.insertAll(categoriesToInsert)
        } catch (e: Exception) {
            // Handle error, e.g., log it
            e.printStackTrace()
        }
    }

    suspend fun addCategory(category: Category) {
        val userId = currentUserId
        val categoryToSave = if (userId == null) {
            category.copy(isPendingSync = true) // Mark as pending sync if offline
        } else {
            category // No change if online
        }

        // Always save to local database first
        categoryDao.insert(categoryToSave)

        if (userId != null) {
            try {
                FirestoreManager.saveCategory(userId, category) // Try to save original category to Firebase
                // If successful, ensure local copy is not marked as pending sync
                if (categoryToSave.isPendingSync) {
                    categoryDao.insert(category.copy(isPendingSync = false)) // Update local status
                }
            } catch (e: Exception) {
                // If Firebase save fails, ensure local copy is marked as pending sync
                categoryDao.insert(category.copy(isPendingSync = true))
                e.printStackTrace() // Log the error
            }
        }
    }
}