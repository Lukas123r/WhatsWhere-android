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
            val localCategories = categoryDao.getCategories().first()
            val firebaseCategories = FirestoreManager.getCategories(userId)

            // Identify local-only categories and push them to Firebase
            val localOnlyCategories = localCategories.filter { localCategory ->
                firebaseCategories.none { it.name == localCategory.name }
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
        val userId = currentUserId ?: return
        // Save to local database
        categoryDao.insert(category)
        // Save to Firebase
        try {
            FirestoreManager.saveCategory(userId, category)
        } catch (e: Exception) {
            // Handle error, e.g., log it
            e.printStackTrace()
        }
    }
}