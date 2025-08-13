package com.example.whatswhere.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import android.util.Log // Import for logging
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.whatswhere.R // Import für R-Klasse, um auf String-Array zuzugreifen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Item::class, Tag::class, Category::class, ItemTagCrossRef::class],
    version = 10, // Erhöhte Version, um Datenbankmigration zu erzwingen und Tags neu zu befüllen
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "item_database"
                )
                    // Wenn du die Datenbankversion erhöhst, weil du einen Index zu Category hinzugefügt hast,
                    // und keine komplexen Migrationen schreiben möchtest, ist fallbackToDestructiveMigration OK.
                    // Für eine Produktions-App wären richtige Migrationen besser.
                    .fallbackToDestructiveMigration()
                    // WICHTIG: Den Callback mit dem App-Kontext übergeben, um auf Ressourcen zugreifen zu können
                    .addCallback(AppDatabaseCallback(CoroutineScope(Dispatchers.IO), context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // AppDatabaseCallback benötigt jetzt den Context, um auf String-Ressourcen zuzugreifen
        private class AppDatabaseCallback(
            private val scope: CoroutineScope,
            private val context: Context // Context hinzugefügt
        ) : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        populateDatabase(database.itemDao())
                    }
                }
            }

            // Methode wird beim Erstellen der DB aufgerufen, um vordefinierte Daten einzufügen
            suspend fun populateDatabase(itemDao: ItemDao) {
                Log.d("AppDatabase", "Populating database with predefined data.")

                // Clear existing data to ensure fresh pre-population
                itemDao.deleteAllCategories()
                itemDao.deleteAllTags()

                // Vordefinierte Kategorien (wie du sie schon hattest)
                // Die nameKeys müssen mit deinen String-Ressourcen übereinstimmen
                val predefinedCategories = listOf(
                    Category(nameKey = "category_general"),
                    Category(nameKey = "category_documents"),
                    Category(nameKey = "category_electronics"),
                    Category(nameKey = "category_clothing"),
                    Category(nameKey = "category_tools")
                )
                // Verwende die neue Methode, um eine Liste einzufügen
                itemDao.insertCategories(predefinedCategories)
                Log.d("AppDatabase", "Inserted predefined categories.")

                // Vordefinierte Tags aus dem String-Array in values/strings.xml laden
                val predefinedTagResourceKeys = context.resources.getStringArray(R.array.predefined_tag_resource_keys)

                val predefinedTags = predefinedTagResourceKeys.map { key ->
                    Tag(nameKey = key, isPredefined = true) // Setze isPredefined auf true
                }
                // Verwende die neue Methode, um eine Liste von Tags einzufügen
                itemDao.insertTags(predefinedTags)
                Log.d("AppDatabase", "Inserted predefined tags.")

                val tagCount = itemDao.getAllTags().first().size
                Log.d("AppDatabase", "Total tags in DB after population: $tagCount")
            }
        }
    }
}
