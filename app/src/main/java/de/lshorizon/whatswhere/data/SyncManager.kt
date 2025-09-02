package de.lshorizon.whatswhere.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import de.lshorizon.whatswhere.InventoryApp
import de.lshorizon.whatswhere.R
import de.lshorizon.whatswhere.ui.util.ConnectivityHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class SyncManager(private val context: Context, private val itemDao: ItemDao) {

    suspend fun syncLocalToCloud() {
        if (!ConnectivityHelper.isOnline(context)) {
            Log.d("SyncManager", "Offline, skipping syncLocalToCloud.")
            return
        }
        val unsyncedItems = itemDao.getUnsyncedItems()
        if (unsyncedItems.isEmpty()) {
            Log.d("SyncManager", "No items to upload.")
            return
        }
        Log.d("SyncManager", "Found ${unsyncedItems.size} items to upload.")

        for (itemToUpload in unsyncedItems) {
            try {
                FirestoreManager.saveItem(itemToUpload.copy(needsSync = false))
                itemDao.update(itemToUpload.copy(needsSync = false))
                Log.d("SyncManager", "Successfully uploaded item ${itemToUpload.id}")
            } catch (e: Exception) {
                Log.e("SyncManager", "Error uploading item ${itemToUpload.id}: ${e.message}", e)
                // leave needsSync=true to retry later
            }
        }
    }

    suspend fun syncCloudToLocal(): SyncResult = withContext(Dispatchers.IO) {
        if (!ConnectivityHelper.isOnline(context)) {
            return@withContext SyncResult.Error(context.getString(R.string.toast_sync_error_no_internet))
        }

        val user = Firebase.auth.currentUser
        if (user == null) {
            return@withContext SyncResult.Error(context.getString(R.string.toast_sync_error_not_logged_in))
        }

        try {
            Log.d("SyncManager", "Starting syncCloudToLocal for user ${user.uid}")
            val firestoreItems = FirestoreManager.getItemsCollection()
                .whereEqualTo("userId", user.uid)
                .get()
                .await()
                .toObjects(Item::class.java)

            Log.d("SyncManager", "Fetched ${firestoreItems.size} items from Firestore.")

            val database = (context.applicationContext as InventoryApp).database

            database.withTransaction {
                val localList = itemDao.getAllItems().first()
                val localById = localList.associateBy { it.id }

                firestoreItems.forEach { fi ->
                    val local = localById[fi.id]
                    if (local == null) {
                        itemDao.insert(fi.copy(needsSync = false))
                        Log.d("SyncManager", "Inserted cloud item ${fi.id}")
                    } else if (!local.needsSync) {
                        itemDao.insert(fi.copy(needsSync = false))
                        Log.d("SyncManager", "Updated local item from cloud ${fi.id}")
                    } else {
                        Log.d("SyncManager", "Skipped overwriting pending local item ${fi.id}")
                    }
                }
            }
            Log.d("SyncManager", "syncCloudToLocal completed successfully.")
            return@withContext SyncResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SyncManager", "Error during syncCloudToLocal: ${e.message}", e)
            return@withContext SyncResult.Error(context.getString(R.string.toast_sync_error_generic, e.message))
        }
    }
}

