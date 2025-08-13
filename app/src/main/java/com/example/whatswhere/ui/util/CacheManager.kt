package com.example.whatswhere.ui.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

object CacheManager {

    /**
     * Berechnet die Größe eines Verzeichnisses rekursiv.
     * @param dir Das Verzeichnis, dessen Größe berechnet werden soll.
     * @return Die Gesamtgröße in Bytes.
     */
    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * Formatiert eine Byte-Größe in einen lesbaren String (B, KB, MB, GB...).
     * @param size Die Größe in Bytes.
     * @return Ein formatierter String wie "12.3 MB".
     */
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Öffentliche Funktion, um die formatierte Größe des App-Caches zu erhalten.
     */
    fun getCacheSize(context: Context): String {
        val cacheDir = context.cacheDir
        val size = getDirectorySize(cacheDir)
        return formatSize(size)
    }

    /**
     * Öffentliche Funktion, um den App-Cache asynchron zu leeren.
     * @param onFinished Ein Callback, der auf dem UI-Thread ausgeführt wird, nachdem der Cache geleert wurde.
     */
    fun clearCache(context: Context, onFinished: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val cacheDir = context.cacheDir
            cacheDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }
}