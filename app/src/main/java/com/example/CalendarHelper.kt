@file:Suppress("DEPRECATION")

package com.example

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object CalendarHelper {
    var client = OkHttpClient()

    suspend fun fetchTodayEvents(context: Context): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val eventsList = mutableListOf<CalendarEvent>()
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account
                ?: return@withContext eventsList

            val token = GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/calendar")
            
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            
            val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val timeMin = rfc3339Format.format(now.time)
            val timeMax = rfc3339Format.format(endOfDay.time)

            // Make a request to the events list endpoint
            val request = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=${java.net.URLEncoder.encode(timeMin, "UTF-8")}&timeMax=${java.net.URLEncoder.encode(timeMax, "UTF-8")}&singleEvents=true&orderBy=startTime")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 402) {
                    println("Google Calendar API returned 402 Payment Required")
                }
                return@withContext eventsList
            }

            val responseBody = response.body?.string() ?: return@withContext eventsList
            val responseJson = JSONObject(responseBody)
            if (!responseJson.has("items")) return@withContext eventsList
            
            val itemsArray = responseJson.getJSONArray("items")
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val id = item.optString("id", "")
                val title = item.optString("summary", "Busy")
                
                var startMs = 0L
                var endMs = 0L
                var isAllDay = false
                
                val startObj = item.optJSONObject("start")
                if (startObj != null) {
                    if (startObj.has("dateTime")) {
                        startMs = rfc3339Format.parse(startObj.getString("dateTime"))?.time ?: 0L
                    } else if (startObj.has("date")) {
                        isAllDay = true
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        startMs = dateFormat.parse(startObj.getString("date"))?.time ?: 0L
                    }
                }
                
                val endObj = item.optJSONObject("end")
                if (endObj != null) {
                    if (endObj.has("dateTime")) {
                        endMs = rfc3339Format.parse(endObj.getString("dateTime"))?.time ?: 0L
                    } else if (endObj.has("date")) {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        endMs = dateFormat.parse(endObj.getString("date"))?.time ?: 0L
                    }
                }
                
                eventsList.add(CalendarEvent(id, title, startMs, endMs, isAllDay))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        eventsList
    }

    suspend fun autoScheduleTasks(context: Context, tasks: List<Task>, viewModel: ConciergeViewModel): String = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account
                ?: return@withContext "Error: Not signed in to Google."

            val token = GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/calendar")
            
            // 1. Get today's events to find free slots
            val now = Calendar.getInstance()
            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            
            val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            
            val timeMin = rfc3339Format.format(now.time)
            val timeMax = rfc3339Format.format(endOfDay.time)

            // Make a freeBusy request
            val freeBusyReq = JSONObject().apply {
                put("timeMin", timeMin)
                put("timeMax", timeMax)
                put("items", JSONArray().put(JSONObject().put("id", "primary")))
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/freeBusy")
                .addHeader("Authorization", "Bearer $token")
                .post(freeBusyReq.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 402) {
                    return@withContext "Calendar API quota exceeded or payment required (Error 402)."
                }
                return@withContext "Failed to fetch calendar data: ${response.code} ${response.message}"
            }

            val responseBody = response.body?.string() ?: return@withContext "Empty response from Google Calendar"
            val responseJson = JSONObject(responseBody)
            val busyArray = responseJson.getJSONObject("calendars").getJSONObject("primary").getJSONArray("busy")

            val busySlots = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until busyArray.length()) {
                val busy = busyArray.getJSONObject(i)
                val start = rfc3339Format.parse(busy.getString("start"))?.time ?: 0L
                val end = rfc3339Format.parse(busy.getString("end"))?.time ?: 0L
                busySlots.add(Pair(start, end))
            }
            busySlots.sortBy { it.first }

            // 2. Map tasks to free slots
            // Start scheduling from 15 minutes from now
            var currentStartTime = now.timeInMillis + 15 * 60 * 1000 
            val scheduledEvents = mutableListOf<String>()

            // We only schedule tasks that don't already have a dueDate
            val unscheduledTasks = tasks.filter { it.dueDate == null }

            for (task in unscheduledTasks) {
                // Assume each task takes 30 mins
                val taskDurationMs = 30 * 60 * 1000L
                val bufferMs = 15 * 60 * 1000L // 15 min buffer/break

                var scheduled = false
                while (true) {
                    val currentEndTime = currentStartTime + taskDurationMs
                    val overlaps = busySlots.any { (start, end) -> 
                        currentStartTime < end && currentEndTime > start
                    }
                    
                    if (currentEndTime > endOfDay.timeInMillis) {
                        break // No time left today
                    }

                    if (!overlaps) {
                        // Book it!
                        insertEvent(token, task.title, task.description, currentStartTime, currentEndTime)
                        scheduledEvents.add("${task.title} at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentStartTime)}")
                        
                        // Update task in Firestore via viewModel
                        viewModel.setTaskDueDate(task, currentStartTime, context)
                        
                        // Add to our busy slots to avoid overlapping with ourselves
                        busySlots.add(Pair(currentStartTime, currentEndTime))
                        busySlots.sortBy { it.first }
                        
                        // Advance time for next task + buffer
                        currentStartTime = currentEndTime + bufferMs
                        scheduled = true
                        break
                    } else {
                        // Move currentStartTime past the overlapping block
                        val overlappingBlock = busySlots.first { (start, end) -> currentStartTime < end && currentEndTime > start }
                        currentStartTime = overlappingBlock.second + bufferMs
                    }
                }
                
                if (!scheduled) {
                    scheduledEvents.add("Could not fit: ${task.title}")
                }
            }

            if (scheduledEvents.isEmpty()) return@withContext "No tasks to schedule or no free time available."
            
            return@withContext "Tasks auto-scheduled in Google Calendar:\n" + scheduledEvents.joinToString("\n")

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Google Calendar Error: ${e.message}"
        }
    }

    private fun insertEvent(token: String, title: String, description: String, startMs: Long, endMs: Long) {
        val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        
        val eventJson = JSONObject().apply {
            put("summary", title)
            put("description", description)
            put("start", JSONObject().put("dateTime", rfc3339Format.format(startMs)))
            put("end", JSONObject().put("dateTime", rfc3339Format.format(endMs)))
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            .addHeader("Authorization", "Bearer $token")
            .post(eventJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response -> 
            if (!response.isSuccessful) {
                println("Failed to insert event: ${response.body?.string()}")
            }
        }
    }
}
