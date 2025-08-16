package com.example.whatswhere

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whatswhere.data.AppDatabase
import com.example.whatswhere.ui.NotificationHelper
import com.example.whatswhere.ui.WarrantyWorker
import com.example.whatswhere.ui.util.LocaleHelper
import com.example.whatswhere.data.dao.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class InventoryApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        // Pre-populate categories
        CoroutineScope(Dispatchers.IO).launch {
            val categoryDao = database.categoryDao()
            val categories = listOf(
                Category(getString(R.string.category_all)),
                Category(getString(R.string.category_documents)),
                Category(getString(R.string.category_electronics)),
                Category(getString(R.string.category_household)),
                Category(getString(R.string.category_miscellaneous)),
                Category(getString(R.string.category_office)),
                Category(getString(R.string.category_tools))
            )
            categoryDao.insertAll(categories)
        }

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