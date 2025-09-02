package de.lshorizon.whatswhere.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import de.lshorizon.whatswhere.data.dao.CategoryDao
import de.lshorizon.whatswhere.data.dao.Category

@Database(
    entities = [Item::class, Category::class],
    version = 17, // Schema change: categories now scoped by userId (composite PK)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao

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
                    .addMigrations(MIGRATION_16_17)
                    .build()
                INSTANCE = instance
                instance
            }
        }
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new categories table with composite primary key (userId, name)
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories_new` (
                        `userId` TEXT NOT NULL DEFAULT '',
                        `name` TEXT NOT NULL,
                        `resourceId` INTEGER NOT NULL DEFAULT 0,
                        `isPendingSync` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`userId`, `name`)
                    )
                    """.trimIndent()
                )
                // Copy old data into new with userId=''
                database.execSQL(
                    """
                    INSERT INTO `categories_new` (`userId`, `name`, `resourceId`, `isPendingSync`)
                    SELECT '' as userId, `name`, COALESCE(`resourceId`,0), COALESCE(`isPendingSync`,0)
                    FROM `categories`
                    """.trimIndent()
                )
                // Replace old table
                database.execSQL("DROP TABLE IF EXISTS `categories`")
                database.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
            }
        }
    }
}
