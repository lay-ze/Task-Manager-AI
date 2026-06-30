package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Task Due"
        var message = intent.getStringExtra("message") ?: "You have a task due now."
        val category = intent.getStringExtra("category") ?: "Personal"
        
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        var priority = NotificationCompat.PRIORITY_DEFAULT
        if (category.equals("Urgent", ignoreCase = true)) {
            priority = NotificationCompat.PRIORITY_MAX
            message = "URGENT: $message"
        } else if (category.equals("Work", ignoreCase = true)) {
            if (currentHour < 8 || currentHour >= 18) {
                priority = NotificationCompat.PRIORITY_LOW
                message = "Outside work hours: $message"
            } else {
                priority = NotificationCompat.PRIORITY_HIGH
            }
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "task_channel",
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, "task_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
