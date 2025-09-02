package de.lshorizon.whatswhere.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import de.lshorizon.whatswhere.R
import java.util.Locale

object CategoryLocaleMapper {

    private val keyToRes: Map<String, Int> = mapOf(
        "all" to R.string.category_all,
        "documents" to R.string.category_documents,
        "electronics" to R.string.category_electronics,
        "household" to R.string.category_household,
        "miscellaneous" to R.string.category_miscellaneous,
        "office" to R.string.category_office,
        "tools" to R.string.category_tools
    )

    private val supportedLocales = listOf("en", "de", "es", "fr", "it")

    fun resIdForKey(key: String): Int? = keyToRes[key]

    fun keyForResId(resId: Int): String? = keyToRes.entries.firstOrNull { it.value == resId }?.key

    fun currentLabelForKey(context: Context, key: String): String? =
        keyToRes[key]?.let { context.getString(it) }

    fun resolveKeyFromText(context: Context, text: String): String? {
        val t = text.trim().lowercase()
        // Direct key match
        if (keyToRes.containsKey(t)) return t

        // Try all supported locales
        for (lang in supportedLocales) {
            val ctx = contextForLocale(context, Locale(lang))
            for ((key, resId) in keyToRes) {
                val localized = ctx.getString(resId).trim().lowercase()
                if (localized == t) return key
            }
        }
        return null
    }

    private fun contextForLocale(base: Context, locale: Locale): Context {
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            return base.createConfigurationContext(config)
        }
    }
}

