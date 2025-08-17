package de.lshorizon.whatswhere.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import de.lshorizon.whatswhere.data.dao.Category

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

    suspend fun saveCategory(category: Category) {
        categoriesCollection.document(category.name).set(category).await()
    }

    suspend fun getCategories(): List<Category> {
        return categoriesCollection.get().await().documents.mapNotNull { it.toObject(Category::class.java) }
    }
}