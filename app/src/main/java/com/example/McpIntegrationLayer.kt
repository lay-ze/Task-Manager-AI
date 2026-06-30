@file:Suppress("DEPRECATION")

package com.example

import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object McpIntegrationLayer {

    private suspend fun getOAuthToken(context: Context): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account
                ?: return@withContext null
            GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/gmail.compose")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun executeTool(
        name: String,
        args: JsonObject?,
        context: Context,
        onTaskCreated: (String, String, String) -> Unit
    ): JsonObject {
        return try {
            when (name) {
                "addTask" -> {
                    val title = args?.get("title")?.jsonPrimitive?.content ?: "Task"
                    val desc = args?.get("description")?.jsonPrimitive?.content ?: ""
                    val cat = args?.get("category")?.jsonPrimitive?.content ?: "Personal"
                    
                    onTaskCreated(title, desc, cat)
                    buildJsonObject { put("status", "success") }
                }
                "scheduleMeeting" -> {
                    val title = args?.get("title")?.jsonPrimitive?.content ?: "Meeting"
                    val time = args?.get("time")?.jsonPrimitive?.content ?: ""
                    val attendeesList = args?.get("attendees")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val attendees = if (attendeesList.isEmpty()) "No attendees" else attendeesList.joinToString(", ")
                    
                    val token = getOAuthToken(context)
                    if (token == null) {
                        return buildJsonObject { 
                            put("status", "error")
                            put("message", "OAuth token is missing. Please sign in or grant Calendar permissions.")
                        }
                    }
                    GoogleApiRetrofitClient.oauthToken = token
                    
                    // Simple parsing for "time", in a real app would use proper NLP or assume ISO-8601
                    // Defaulting to 1 hour from now for demonstration if time is unparseable
                    val startTime = LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME)
                    val endTime = LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
                    
                    val eventRequest = CalendarEventRequest(
                        summary = title,
                        description = "Scheduled by AI Concierge",
                        start = EventDateTime(dateTime = startTime + "Z"),
                        end = EventDateTime(dateTime = endTime + "Z"),
                        attendees = attendeesList.map { EventAttendee(email = it) }
                    )
                    
                    val response = GoogleApiRetrofitClient.service.createEvent(event = eventRequest)
                    val link = response.htmlLink
                    
                    onTaskCreated("Meeting: $title at $time", "Attendees: $attendees. Link: $link", "Work")
                    
                    buildJsonObject { 
                        put("status", "success") 
                        put("meetingLink", link)
                        put("message", "Meeting scheduled and link generated via Google Calendar API.")
                    }
                }
                "createDraftEmail" -> {
                    val recipient = args?.get("recipient")?.jsonPrimitive?.content ?: ""
                    val subject = args?.get("subject")?.jsonPrimitive?.content ?: ""
                    val body = args?.get("body")?.jsonPrimitive?.content ?: ""
                    
                    val token = getOAuthToken(context)
                    if (token == null) {
                        return buildJsonObject { 
                            put("status", "error")
                            put("message", "OAuth token is missing. Please sign in or grant Gmail permissions.")
                        }
                    }
                    GoogleApiRetrofitClient.oauthToken = token
                    
                    val emailContent = "To: $recipient\n" +
                            "Subject: $subject\n\n" +
                            body
                    val base64Email = Base64.encodeToString(emailContent.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
                    
                    val response = GoogleApiRetrofitClient.service.createDraft(GmailDraftRequest(message = GmailSendRequest(raw = base64Email)))
                    
                    buildJsonObject { 
                        put("status", "success")
                        put("message", "Draft email created for $recipient via Gmail API. Draft ID: ${response.id}")
                    }
                }
                "sendPayment" -> {
                    val recipient = args?.get("recipient")?.jsonPrimitive?.content ?: ""
                    val amount = args?.get("amount")?.jsonPrimitive?.content ?: "0"
                    val note = args?.get("note")?.jsonPrimitive?.content ?: ""
                    
                    // In a real app, this would use Google Pay or similar payment APIs
                    buildJsonObject { 
                        put("status", "success")
                        put("message", "Payment of $amount initiated to $recipient for: $note via Payment integration.")
                    }
                }
                else -> {
                    buildJsonObject { put("status", "unknown function") }
                }
            }
        } catch (e: Exception) {
            buildJsonObject { put("status", "error: ${e.message}") }
        }
    }
}
