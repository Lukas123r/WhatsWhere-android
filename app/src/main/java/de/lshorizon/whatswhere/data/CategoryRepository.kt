package de.lshorizon.whatswhere.data

import de.lshorizon.whatswhere.data.dao.CategoryDao
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import de.lshorizon.whatswhere.R

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log

class CategoryRepository(private val categoryDao: CategoryDao) {

    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    // Reaktiver Kategorien-Flow, der auf Auth-Änderungen umschaltet
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val userIdFlow: Flow<String> = callbackFlow {
        val auth = Firebase.auth
        // initial emit
        trySend(auth.currentUser?.uid ?: "")
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid ?: "")
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun getCategories(): Flow<List<Category>> = userIdFlow.flatMapLatest { uid ->
        categoryDao.getCategoriesForUser(uid)
    }

    suspend fun syncCategories() {
        val userId = currentUserId ?: ""
        if (userId.isEmpty()) {
            // Not logged in: nothing to sync with cloud yet
            return
        }
        try {
            Log.d("CategoryRepo", "syncCategories: start for userId='$userId'")
            val defaultNames = setOf(
                "all", "documents", "electronics", "household", "miscellaneous", "office", "tools"
            )
            // 1. Sync pending local categories to Firebase (for this user)
            val pendingAll = categoryDao.getCategoriesForUser(userId).first().filter { it.isPendingSync }
            val pendingForUser = pendingAll.filter { it.userId == userId && it.name.lowercase() !in defaultNames }
            val pendingAnon = pendingAll.filter { it.userId.isEmpty() && it.name.lowercase() !in defaultNames }

            // 1a) Push user-owned pending categories
            pendingForUser.forEach { category ->
                try {
                    FirestoreManager.saveCategory(userId, category.copy(userId = userId))
                    // If successful, update local category to not be pending sync
                    categoryDao.insert(category.copy(userId = userId, isPendingSync = false))
                    Log.d("CategoryRepo", "synced pending category '${category.name}' to cloud")
                } catch (e: Exception) {
                    // Log error but don't stop sync, category remains pending
                    Log.e("CategoryRepo", "error syncing pending category '${category.name}': ${e.message}", e)
                }
            }

            // 1b) Assign anonymous pending categories (created while logged out) to this user and push
            pendingAnon.forEach { category ->
                try {
                    // Remove anonymous row to avoid composite PK duplicate
                    categoryDao.deleteUserCategoryByName("", category.name)
                    val reassigned = category.copy(userId = userId, isPendingSync = false)
                    FirestoreManager.saveCategory(userId, reassigned)
                    categoryDao.insert(reassigned)
                    Log.d("CategoryRepo", "claimed pending anonymous category '${category.name}' for user '$userId'")
                } catch (e: Exception) {
                    Log.e("CategoryRepo", "error claiming anonymous category '${category.name}': ${e.message}", e)
                }
            }

            // 2. Now proceed with the existing sync logic (Firebase to local, and local-only to Firebase)
            val localCategories = categoryDao.getCategoriesForUser(userId).first() // Re-fetch after pending sync
            val firebaseCategories = FirestoreManager.getCategories(userId)
            Log.d("CategoryRepo", "cloud categories fetched: ${firebaseCategories.size}")

            // Identify local-only categories (that are not pending sync anymore) and push them to Firebase
            val localOnlyCategories = localCategories
                .filter { it.userId.isNotEmpty() && it.name.lowercase() !in defaultNames }
                .filter { localCategory ->
                    !localCategory.isPendingSync && firebaseCategories.none { it.name.equals(localCategory.name, ignoreCase = true) }
                }
            localOnlyCategories.forEach { localCategory ->
                FirestoreManager.saveCategory(userId, localCategory.copy(userId = userId))
                Log.d("CategoryRepo", "pushed local-only category '${localCategory.name}' to cloud")
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

            // Cleanup: remove user-duplicates of defaults locally
            defaultNames.forEach { dn ->
                categoryDao.deleteUserCategoryByName(userId, dn)
            }

            val categoriesToInsert = firebaseCategories
                .filter { it.name.lowercase() !in defaultNames }
                .map { firebaseCategory ->
                val resourceId = predefinedCategoryNames[firebaseCategory.name.lowercase()] ?: 0
                firebaseCategory.copy(resourceId = resourceId, userId = userId, isPendingSync = false)
            }

            categoryDao.insertAll(categoriesToInsert)
            Log.d("CategoryRepo", "inserted/updated ${categoriesToInsert.size} categories from cloud locally")
        } catch (e: Exception) {
            // Handle error, e.g., log it
            Log.e("CategoryRepo", "syncCategories error: ${e.message}", e)
        }
    }

    suspend fun addCategory(category: Category) {
        val trimmedName = category.name.trim()
        if (trimmedName.isEmpty()) return

        val userId = currentUserId ?: ""

        // Duplicate check (case-insensitive) against user's categories and defaults
        val existing = categoryDao.findByNameForUser(userId, trimmedName)
        if (existing != null) {
            // Already exists: nothing to do
            Log.d("CategoryRepo", "addCategory skipped, exists: '$trimmedName' for userId='$userId'")
            return
        }

        val base = category.copy(name = trimmedName, userId = userId)

        val localToSave = if (userId.isEmpty()) base.copy(isPendingSync = true) else base
        // Always save to local database first
        categoryDao.insert(localToSave)
        Log.d("CategoryRepo", "added category locally: '${base.name}', userId='$userId', pending=${localToSave.isPendingSync}")

        if (userId.isNotEmpty()) {
            try {
                FirestoreManager.saveCategory(userId, base)
                if (localToSave.isPendingSync) {
                    categoryDao.insert(base.copy(isPendingSync = false))
                }
                Log.d("CategoryRepo", "saved category to cloud: '${base.name}' for userId='$userId'")
            } catch (e: Exception) {
                // If Firebase save fails, ensure local copy is marked as pending sync
                categoryDao.insert(base.copy(isPendingSync = true))
                Log.e("CategoryRepo", "error saving category to cloud: '${base.name}' -> ${e.message}", e)
            }
        }
    }
}
