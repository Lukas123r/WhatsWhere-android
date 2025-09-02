package de.lshorizon.whatswhere

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import de.lshorizon.whatswhere.data.AppDatabase
import de.lshorizon.whatswhere.data.CategoryRepository
import de.lshorizon.whatswhere.data.Item
import de.lshorizon.whatswhere.ui.NotificationHelper
import de.lshorizon.whatswhere.ui.WarrantyWorker
import de.lshorizon.whatswhere.ui.util.LocaleHelper
import de.lshorizon.whatswhere.data.dao.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import de.lshorizon.whatswhere.util.CategoryLocaleMapper
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

        // Standardkategorien ggf. initial befüllen
        populateDefaultCategoriesIfNeeded()

        // Kategorien mit Cloud synchronisieren (früh im App-Start)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                categoryRepository.syncCategories()
            } catch (_: Exception) {
                // Ignorieren, z. B. offline – ViewModel-Refresh synchronisiert später erneut
            }
        }

        // Einmalige Normalisierung: alte Items auf kanonische Kategorien-Keys + Resource-IDs migrieren
        normalizeExistingItemCategoriesIfNeeded()
    }

    private fun populateDefaultCategoriesIfNeeded() { // Renamed function
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstRun = sharedPreferences.getBoolean("is_first_run_categories", true)

        CoroutineScope(Dispatchers.IO).launch {
            val categoryDao = database.categoryDao()
            val needsSeeding = isFirstRun || categoryDao.countAll() == 0
            if (needsSeeding) {
                val categories = listOf(
                    // Defaults: userId = "" (global defaults always available)
                    Category("all", R.string.category_all),
                    Category("documents", R.string.category_documents),
                    Category("electronics", R.string.category_electronics),
                    Category("household", R.string.category_household),
                    Category("miscellaneous", R.string.category_miscellaneous),
                    Category("office", R.string.category_office),
                    Category("tools", R.string.category_tools)
                )
                categoryDao.insertAll(categories)
                sharedPreferences.edit().putBoolean("is_first_run_categories", false).apply()
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val warrantyCheckRequest = PeriodicWorkRequestBuilder<WarrantyWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "warranty_check_work",
            ExistingPeriodicWorkPolicy.KEEP,
            warrantyCheckRequest
        )
    }

    private fun normalizeExistingItemCategoriesIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val done = prefs.getBoolean("categories_normalized_v1", false)
        if (done) return

        CoroutineScope(Dispatchers.IO).launch {
            val itemDao = database.itemDao()
            try {
                val items = itemDao.getAllItems().first()
                if (items.isEmpty()) {
                    prefs.edit().putBoolean("categories_normalized_v1", true).apply()
                    return@launch
                }

                val resToKey = mapOf(
                    R.string.category_all to "all",
                    R.string.category_documents to "documents",
                    R.string.category_electronics to "electronics",
                    R.string.category_household to "household",
                    R.string.category_miscellaneous to "miscellaneous",
                    R.string.category_office to "office",
                    R.string.category_tools to "tools"
                )

                var changed = false
                items.forEach { item ->
                    var newKey: String? = null
                    var newResId: Int = item.categoryResourceId

                    if (item.categoryResourceId != 0) {
                        // Wenn Resource gesetzt ist, versuche den Key davon abzuleiten
                        resToKey[item.categoryResourceId]?.let { key ->
                            newKey = key
                            newResId = CategoryLocaleMapper.resIdForKey(key) ?: 0
                        }
                    } else {
                        val current = item.category
                        // Versuche Key direkt oder via lokalisierte Strings (über alle unterstützten Sprachen)
                        val resolved = CategoryLocaleMapper.resolveKeyFromText(this@InventoryApp, current)
                        if (resolved != null) {
                            newKey = resolved
                            newResId = CategoryLocaleMapper.resIdForKey(resolved) ?: 0
                        }
                    }

                    if (newKey != null) {
                        if (item.category != newKey || item.categoryResourceId != newResId) {
                            itemDao.update(item.copy(category = newKey!!, categoryResourceId = newResId, needsSync = true))
                            changed = true
                        }
                    }
                }

                if (changed) {
                    // nichts weiter zu tun; SyncManager wird bei Refresh hochladen
                }
                prefs.edit().putBoolean("categories_normalized_v1", true).apply()
            } catch (_: Exception) {
                // still try next start
            }
        }
    }
}
