package com.example.whatswhere.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.whatswhere.InventoryApp
import com.example.whatswhere.R
import com.example.whatswhere.ui.util.ConnectivityHelper
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

        // Wir müssen hier die ItemWithTags laden, um an die nameKeys der Tags zu kommen
        // für die Erstellung des `tagsString` für Firestore.
        val itemsWithTagsToSync = itemDao.getAllItemsWithTags().first()
            .filter { localItemWithTags -> unsyncedItems.any { it.id == localItemWithTags.item.id } }


        for (itemWithTags in itemsWithTagsToSync) {
            try {
                // Erstelle den `tagsString` aus den `nameKey`s der Tags
                val tagKeys = itemWithTags.tags.map { it.nameKey }
                val itemToUpload = itemWithTags.item.copy(
                    tagsString = tagKeys.joinToString(";"),
                    needsSync = false // Wird in Firestore direkt als synchronisiert markiert
                )

                FirestoreManager.saveItem(itemToUpload) // FirestoreManager.saveItem sollte das Item-Objekt direkt speichern
                itemDao.update(itemToUpload) // Lokales Item als synchronisiert markieren (needsSync = false)
                Log.d("SyncManager", "Successfully uploaded item ${itemToUpload.id}")
            } catch (e: Exception) {
                Log.e("SyncManager", "Error uploading item ${itemWithTags.item.id}: ${e.message}", e)
                // Optional: Hier könntest du das Item nicht als synchronisiert markieren,
                // damit es beim nächsten Mal erneut versucht wird.
                // Aktuell wird es auch bei Fehler als synchronisiert markiert,
                // wenn FirestoreManager.saveItem fehlschlägt, aber itemDao.update erfolgreich ist.
                // Überlege dir die Fehlerbehandlungsstrategie.
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
            // Annahme: Dein Item-Objekt, das aus Firestore kommt, enthält 'tagsString'
            val firestoreItems = FirestoreManager.getItemsCollection()
                .whereEqualTo("userId", user.uid)
                .get()
                .await()
                .toObjects<Item>() // Stellt sicher, dass Item die 'tagsString' Eigenschaft hat

            Log.d("SyncManager", "Fetched ${firestoreItems.size} items from Firestore.")

            val database = (context.applicationContext as InventoryApp).database

            database.withTransaction {
                // 1. Hole alle lokalen Items des aktuellen Benutzers (nur die Item-Entities)
                //    Wir benötigen die IDs, um die zugehörigen ItemTagCrossRefs zu löschen.
                //    Alternative: Wenn `deleteAllItemsByUserId` kaskadierend löscht, ist das nicht nötig.
                //    Sicherheitshalber löschen wir die CrossRefs explizit.
                val localUserItems = itemDao.getAllItemsWithTags().first()
                    .filter { it.item.userId == user.uid }

                localUserItems.forEach { itemWithTags ->
                    itemDao.deleteTagsByItemId(itemWithTags.item.id)
                }

                // 2. Lösche alle Items des Benutzers aus der lokalen Datenbank
                itemDao.deleteAllItemsByUserId(user.uid)
                Log.d("SyncManager", "Deleted local items and their tag associations for user ${user.uid}")

                // 3. Füge die Items aus Firestore ein und erstelle Tags/Verknüpfungen
                firestoreItems.forEach { firestoreItem ->
                    // Das firestoreItem sollte seine ursprüngliche ID aus Firestore haben.
                    // Beim Einfügen wird diese ID verwendet, da `onConflict = OnConflictStrategy.REPLACE`
                    // in `ItemDao.insert(item: Item)` (falls du es so definiert hast)
                    // oder wenn die ID Teil des PrimaryKeys ist und nicht autoGenerate.
                    // Falls Item.id autoGenerate ist und du die Firestore ID behalten willst,
                    // müsstest du dein Item-Objekt und Dao.insert anpassen.
                    // Für den Moment nehmen wir an, firestoreItem.id ist die persistente ID.
                    itemDao.insert(firestoreItem.copy(needsSync = false)) // Als bereits synchronisiert markieren
                    Log.d("SyncManager", "Inserted/Updated item ${firestoreItem.id} from Firestore.")

                    val tagKeysOrNames = if (firestoreItem.tagsString.isNullOrBlank()) {
                        emptyList()
                    } else {
                        firestoreItem.tagsString!!.split(";")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }

                    if (tagKeysOrNames.isNotEmpty()) {
                        val itemTagCrossRefs = mutableListOf<ItemTagCrossRef>()
                        tagKeysOrNames.forEach { keyOrName ->
                            var tag = itemDao.getTagByNameKey(keyOrName)
                            if (tag == null) {
                                // Tag existiert nicht, als benutzerdefiniertes Tag erstellen
                                val newTag = Tag(nameKey = keyOrName, isPredefined = false)
                                val newTagId = itemDao.insertTag(newTag)
                                tag = Tag(id = newTagId, nameKey = keyOrName, isPredefined = false)
                                Log.d("SyncManager", "Created new local tag '${keyOrName}' with id ${tag.id}")
                            }
                            itemTagCrossRefs.add(ItemTagCrossRef(firestoreItem.id, tag.id))
                        }
                        itemDao.insertItemTagCrossRefs(itemTagCrossRefs)
                        Log.d("SyncManager", "Associated ${itemTagCrossRefs.size} tags with item ${firestoreItem.id}")
                    }
                }
                // 4. Lösche verwaiste Tags (Tags ohne Verknüpfung zu irgendeinem Item)
                itemDao.deleteOrphanedTags()
                Log.d("SyncManager", "Deleted orphaned tags.")
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
