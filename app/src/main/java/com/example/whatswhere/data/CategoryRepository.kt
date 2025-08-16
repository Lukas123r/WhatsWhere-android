package com.example.whatswhere.data

import com.example.whatswhere.data.dao.CategoryDao
import com.example.whatswhere.data.dao.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.example.whatswhere.R

class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getCategories(): Flow<List<Category>> = flow {
        // First, emit categories from local database
        emit(categoryDao.getCategories().first())

        // Then, try to fetch from Firebase
        try {
            val firebaseCategories = FirestoreManager.getCategories()
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
            // Emit the updated list (which now includes Firebase categories)
            emit(categoryDao.getCategories().first())
        } catch (e: Exception) {
            // Handle error, e.g., log it
            e.printStackTrace()
        }
    }

    suspend fun addCategory(category: Category) {
        // Save to local database
        categoryDao.insert(category)
        // Save to Firebase
        try {
            FirestoreManager.saveCategory(category)
        } catch (e: Exception) {
            // Handle error, e.g., log it
            e.printStackTrace()
        }
    }
}