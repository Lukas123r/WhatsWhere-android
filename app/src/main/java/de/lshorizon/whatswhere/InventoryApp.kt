package de.lshorizon.whatswhere

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.lshorizon.whatswhere.data.AppDatabase
import de.lshorizon.whatswhere.data.CategoryRepository
import de.lshorizon.whatswhere.ui.NotificationHelper
import de.lshorizon.whatswhere.ui.WarrantyWorker
import de.lshorizon.whatswhere.ui.util.LocaleHelper
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class InventoryApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }

    override fun onCreate() {
        super.onCreate()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Design-Einstellung laden
        val themeValue = sharedPreferences.getString("theme", "system")
        applyTheme(themeValue)

        // Sprach-Einstellung laden
        val languageCode = sharedPreferences.getString("language", "en") ?: "en"
        LocaleHelper.setLocale(languageCode)

        // Benachrichtigungskanal erstellen und Worker planen
        NotificationHelper.createNotificationChannel(this)
        scheduleWarrantyChecks()
    }

    fun repopulateCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val categoryDao = database.categoryDao()
            categoryDao.deleteAll()
            val categories = listOf(
                Category("category_all", R.string.category_all),
                Category("category_documents", R.string.category_documents),
                Category("category_electronics", R.string.category_electronics),
                Category("category_household", R.string.category_household),
                Category("category_miscellaneous", R.string.category_miscellaneous),
                Category("category_office", R.string.category_office),
                Category("category_tools", R.string.category_tools)
            )
            categoryDao.insertAll(categories)
        }
    }

    private fun applyTheme(themeValue: String?) {
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun scheduleWarrantyChecks() {
        val warrantyCheckRequest =
            PeriodicWorkRequestBuilder<WarrantyWorker>(24, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "warranty_check_work",
            ExistingPeriodicWorkPolicy.KEEP,
            warrantyCheckRequest
        )
    }
}