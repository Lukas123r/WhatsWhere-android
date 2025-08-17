package de.lshorizon.whatswhere.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import de.lshorizon.whatswhere.R

object NotificationHelper {

    private const val CHANNEL_ID = "warranty_notifications"
    private const val CHANNEL_NAME = "Warranty Expirations"
    private const val CHANNEL_DESCRIPTION = "Notifications for items with expiring warranties"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showWarrantyNotification(context: Context, expiringItems: List<String>) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = "Warranty Alert!"
        val content = "You have ${expiringItems.size} item(s) with expiring warranties: ${expiringItems.joinToString(", ")}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications) // Unser existierendes Icon
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // ID ist eindeutig, damit nicht mehrere Benachrichtigungen gleichzeitig erscheinen
        notificationManager.notify(1, notification)
    }
}