package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class DailyBriefingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return Result.success()

        val firestore = FirebaseFirestore.getInstance()
        
        try {
            val snapshot = firestore.collection("tasks")
                .whereEqualTo("userId", user.uid)
                .get()
                .await()
                
            val tasks = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val title = doc.getString("title") ?: ""
                val completed = doc.getBoolean("completed") ?: false
                val priority = doc.getString("priority") ?: "Medium"
                if (!completed) Pair(title, priority) else null
            }
            
            val highPriority = tasks.count { it.second == "High" }
            val workloadStr = if (tasks.size >= 5) "Heavy (${tasks.size} tasks scheduled, $highPriority High Priority)" else "Light (${tasks.size} tasks scheduled, $highPriority High Priority)"

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) return Result.success()
            
            val promptText = """
                {
                  "last7Days": {
                    "Mon": {"completed": 5, "missed": 0},
                    "Tue": {"completed": 4, "missed": 1},
                    "Wed": {"completed": 6, "missed": 0},
                    "Thu": {"completed": 2, "missed": 3},
                    "Fri": {"completed": 1, "missed": 4, "note": "Missed tasks mostly occurred after 4 PM"}
                  },
                  "todayWorkload": "$workloadStr"
                }
            """.trimIndent()
            
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = promptText)), role = "user")),
                systemInstruction = Content(parts = listOf(Part(text = """
                    You are a proactive, encouraging productivity coach. You will be given the user's task completion data for the last 7 days and an overview of their workload for today.
                    Your goal is to write a single, punchy, actionable notification (max 2 sentences, under 150 characters) to be sent as a morning push notification.
                    Do not be generic. Reference a specific trend from the data and suggest a specific strategy for today.
                    
                    Output your response strictly as a JSON object matching the requested schema. Do not include markdown formatting like ```json in the final output.
                    
                    {
                      "type": "object",
                      "properties": {
                        "pushNotificationText": {
                          "type": "string",
                          "description": "Short, actionable, personalized advice."
                        },
                        "suggestedAppAction": {
                          "type": "string",
                          "enum": ["OPEN_DASHBOARD", "TRIGGER_AUTO_SCHEDULE", "SUGGEST_BREAK"],
                          "description": "Internal intent to trigger in the Android app when notification is tapped."
                        }
                      }
                    }
                """.trimIndent())))
            )
            
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val jsonString = replyText.replace(Regex("```json|```", RegexOption.IGNORE_CASE), "").trim()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val parsedResult = json.decodeFromString<DailyBriefingResult>(jsonString)
            
            showNotification(parsedResult.pushNotificationText, parsedResult.suggestedAppAction)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    
    private fun showNotification(text: String, action: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_briefing_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Briefing",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("action", action)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val dashboardIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("action", "OPEN_DASHBOARD")
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            applicationContext, 1, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val autoScheduleIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("action", "TRIGGER_AUTO_SCHEDULE")
        }
        val autoSchedulePendingIntent = PendingIntent.getActivity(
            applicationContext, 2, autoScheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val breakIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("action", "SUGGEST_BREAK")
        }
        val breakPendingIntent = PendingIntent.getActivity(
            applicationContext, 3, breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Morning Briefing")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_agenda, "Dashboard", dashboardPendingIntent)
            .addAction(android.R.drawable.ic_menu_today, "Auto-Schedule", autoSchedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Take Break", breakPendingIntent)
            
        notificationManager.notify(1001, builder.build())
    }
}
