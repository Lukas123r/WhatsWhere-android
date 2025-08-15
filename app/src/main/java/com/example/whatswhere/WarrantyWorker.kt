package com.example.whatswhere.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatswhere.InventoryApp
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class WarrantyWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Prüfen, ob die App die Berechtigung zum Senden von Benachrichtigungen hat
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Wenn keine Berechtigung, Arbeit abbrechen
            return Result.failure()
        }

        val itemDao = (applicationContext as InventoryApp).database.itemDao()
        val allItems = itemDao.getAllItems().first()
        val expiringItems = mutableListOf<String>()

        val today = LocalDate.now()
        val thirtyDaysFromNow = today.plusDays(30)

        allItems.forEach { item ->
            item.warrantyExpiration?.let { timestamp ->
                val expirationDate = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                // Prüfen, ob das Ablaufdatum in der Zukunft liegt und innerhalb der nächsten 30 Tage
                if (expirationDate.isAfter(today) && expirationDate.isBefore(thirtyDaysFromNow)) {
                    expiringItems.add(item.name)
                }
            }
        }

        if (expiringItems.isNotEmpty()) {
            NotificationHelper.showWarrantyNotification(applicationContext, expiringItems)
        }

        return Result.success()
    }
}