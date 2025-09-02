package de.lshorizon.whatswhere.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.lshorizon.whatswhere.InventoryApp
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WarrantyWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Android 13+ benötigt Benachrichtigungsberechtigung
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure()
            }
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

