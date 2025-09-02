package de.lshorizon.whatswhere.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import de.lshorizon.whatswhere.data.dao.Category
import android.util.Log

object FirestoreManager {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val itemsCollection = db.collection("items")
    private val categoriesCollection = db.collection("categories")

    suspend fun getOrCreateUserProfile(firebaseUser: FirebaseUser): User {
        val docRef = usersCollection.document(firebaseUser.uid)
        val documentSnapshot = docRef.get().await()

        if (documentSnapshot.exists()) {
            return documentSnapshot.toObject(User::class.java)!!
        } else {
            val newUserProfile = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                name = firebaseUser.displayName ?: "",
                photoUrl = firebaseUser.photoUrl?.toString() ?: ""
            )
            docRef.set(newUserProfile).await()
            return newUserProfile
        }
    }

    suspend fun createUserProfileDocument(firebaseUser: FirebaseUser) {
        val userProfile = User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName ?: "",
            photoUrl = firebaseUser.photoUrl?.toString() ?: ""
        )
        usersCollection.document(firebaseUser.uid).set(userProfile).await()
    }

    suspend fun updateUserName(uid: String, newName: String) {
        usersCollection.document(uid).update("name", newName).await()
    }

    fun getItemsCollection() = itemsCollection

    suspend fun saveItem(item: Item) {
        itemsCollection.document(item.id).set(item).await()
    }

    suspend fun deleteItem(item: Item) {
        itemsCollection.document(item.id).delete().await()
    }

    private fun getUserCategoriesCollection(userId: String) = usersCollection.document(userId).collection("categories")

    suspend fun saveCategory(userId: String, category: Category) {
        // Erzwinge konsistente Felder im gespeicherten Dokument
        val payload = category.copy(userId = userId, isPendingSync = false)
        Log.d("FirestoreMgr", "saveCategory: userId='$userId', name='${category.name}'")
        getUserCategoriesCollection(userId).document(category.name).set(payload).await()
    }

    suspend fun getCategories(userId: String): List<Category> {
        Log.d("FirestoreMgr", "getCategories for userId='$userId'")
        return getUserCategoriesCollection(userId).get().await().documents.mapNotNull { it.toObject(Category::class.java) }
    }
}
