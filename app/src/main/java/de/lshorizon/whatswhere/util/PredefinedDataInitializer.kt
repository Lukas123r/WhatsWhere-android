package de.lshorizon.whatswhere.util

import android.content.Context
import android.content.SharedPreferences
import de.lshorizon.whatswhere.data.ItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PredefinedDataInitializer {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_PREDEFINED_DATA_INITIALIZED = "predefined_data_initialized"

    suspend fun initialize(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_PREDEFINED_DATA_INITIALIZED, false)

        if (!initialized) {
            withContext(Dispatchers.IO) {
                
                // This initializer will be empty for now.
                prefs.edit().putBoolean(KEY_PREDEFINED_DATA_INITIALIZED, true).apply()
            }
        }
    }
}