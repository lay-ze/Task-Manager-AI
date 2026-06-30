package com.example

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.FileOutputStream

class ConciergeViewModel : ViewModel() {
    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEvent>> = _calendarEvents.asStateFlow()

    fun fetchCalendarEvents(context: Context) {
        viewModelScope.launch {
            val events = CalendarHelper.fetchTodayEvents(context)
            _calendarEvents.value = events
        }
    }

    private val _panicModeResult = MutableStateFlow<PanicModeResult?>(null)
    val panicModeResult: StateFlow<PanicModeResult?> = _panicModeResult.asStateFlow()

    private val _dailyBriefing = MutableStateFlow<DailyBriefingResult?>(null)
    val dailyBriefing: StateFlow<DailyBriefingResult?> = _dailyBriefing.asStateFlow()

    fun generateDailyBriefing() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return
        
        _isLoading.value = true
        _dailyBriefing.value = null
        
        viewModelScope.launch {
            try {
                val pending = _tasks.value.filter { !it.completed }
                val highPriority = pending.count { it.priority == "High" }
                val workloadStr = if (pending.size >= 5) "Heavy (${pending.size} tasks scheduled, $highPriority High Priority)" else "Light (${pending.size} tasks scheduled, $highPriority High Priority)"

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
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                val parsedResult = json.decodeFromString<DailyBriefingResult>(jsonString)
                _dailyBriefing.value = parsedResult
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearDailyBriefing() {
        _dailyBriefing.value = null
    }

    fun triggerPanicMode() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return
        
        _isLoading.value = true
        _panicModeResult.value = null
        
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val pending = _tasks.value.filter { !it.completed }
                val tasksJson = pending.joinToString(", ") { 
                    """{"id": "${it.id}", "title": "${it.title}", "dueDate": "${it.dueDate ?: ""}", "priority": "${it.priority}"}"""
                }
                
                val eventsJson = _calendarEvents.value.filter { it.startMs > now }.joinToString(", ") {
                    """{"id": "evt_${it.id}", "title": "${it.title}", "type": "meeting"}"""
                }
                
                val promptText = """
                    {
                      "currentTime": "$now",
                      "freeTimeRemainingTodayHours": 4.0,
                      "pendingTasks": [$tasksJson, $eventsJson]
                    }
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)), role = "user")),
                    systemInstruction = Content(parts = listOf(Part(text = """
                        You are an elite, empathetic productivity assistant. The user is currently in "Panic Mode"—they are overwhelmed, stressed, and have too many imminent deadlines and not enough time.
                        Your job is to perform ruthless triage. You must analyze their pending tasks and current schedule to:
                        1. Isolate the absolute critical "Minimum Viable Actions" they MUST do right now to survive the day.
                        2. Identify tasks that can be safely rescheduled and suggest a new time.
                        3. Identify tasks or meetings that should be canceled or delegated.
                        4. Draft polite, professional apology messages or cancellation emails for any missed commitments or meetings they need to skip.
                        
                        Output your response strictly as a JSON object matching the requested schema. Do not include markdown formatting like ```json in the final output.
                        
                        {
                          "type": "object",
                          "properties": {
                            "minimumViableActions": {
                              "type": "array",
                              "items": { "type": "string" },
                              "description": "1 or 2 tiny, immediate steps to get the user un-stuck (e.g., 'Open the tax portal')."
                            },
                            "reschedule": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "properties": {
                                  "taskId": { "type": "string" },
                                  "suggestedNewTime": { "type": "string" },
                                  "reasoning": { "type": "string" }
                                }
                              }
                            },
                            "cancelOrDelegate": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "properties": {
                                  "taskId": { "type": "string" },
                                  "draftedMessage": { "type": "string" }
                                }
                              }
                            },
                            "comfortingMessage": {
                              "type": "string",
                              "description": "A short, empathetic sentence to calm the user down."
                            }
                          }
                        }
                    """.trimIndent())))
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                
                val jsonString = replyText.replace(Regex("```json|```", RegexOption.IGNORE_CASE), "").trim()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                val parsedResult = json.decodeFromString<PanicModeResult>(jsonString)
                _panicModeResult.value = parsedResult
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearPanicModeResult() {
        _panicModeResult.value = null
    }

    fun processBrainDump(transcript: String, context: Context) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val promptText = """
                    Parse the following brain dump transcript into structured tasks.
                    Transcript: "$transcript"
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)), role = "user")),
                    systemInstruction = Content(parts = listOf(Part(text = """
                        You are an AI that extracts tasks from raw voice transcripts.
                        Return the output strictly as a JSON object matching the requested schema.
                        Do not include markdown formatting like ```json in the final output.
                        
                        {
                          "type": "object",
                          "properties": {
                            "tasks": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "properties": {
                                  "title": { "type": "string" },
                                  "description": { "type": "string" },
                                  "category": { "type": "string", "enum": ["Work", "Personal", "Errands"] },
                                  "deadlineDays": { "type": "integer", "description": "Number of days until the deadline, if mentioned. Null otherwise." }
                                },
                                "required": ["title", "description", "category"]
                              }
                            }
                          },
                          "required": ["tasks"]
                        }
                    """.trimIndent())))
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val jsonString = replyText.replace(Regex("```json|```", RegexOption.IGNORE_CASE), "").trim()
                
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                val parsedResult = json.decodeFromString<BrainDumpResponse>(jsonString)
                
                for (task in parsedResult.tasks) {
                    val dueDate = task.deadlineDays?.let { System.currentTimeMillis() + it * 24L * 60 * 60 * 1000 }
                    val newTask = Task(
                        title = task.title,
                        description = task.description,
                        category = task.category,
                        dueDate = dueDate,
                        userId = _user.value?.uid ?: "guest"
                    )
                    
                    if (_user.value != null && !_isGuest.value) {
                        val ref = firestore.collection("tasks").document()
                        ref.set(newTask.copy(id = ref.id)).await()
                    } else {
                        val updatedList = _tasks.value.toMutableList()
                        updatedList.add(newTask.copy(id = java.util.UUID.randomUUID().toString()))
                        _tasks.value = updatedList
                    }
                }
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Added ${parsedResult.tasks.size} tasks from brain dump!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to parse brain dump.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _chatLog = MutableStateFlow<List<Content>>(emptyList())
    val chatLog: StateFlow<List<Content>> = _chatLog.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isGuest = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance().apply {
        // Enable offline persistence
        @Suppress("DEPRECATION")
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    init {
        _user.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            _user.value = currentUser
            if (currentUser != null) {
                _isGuest.value = false
                loadTasks(currentUser.uid)
            } else {
                if (!_isGuest.value) {
                    _tasks.value = emptyList()
                }
            }
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        _isGuest.value = isGuest
        if (isGuest) {
            loadTasks("guest_user_id")
        } else {
            val currentUser = _user.value
            if (currentUser != null) {
                loadTasks(currentUser.uid)
            } else {
                _tasks.value = emptyList()
            }
        }
    }

    private fun loadTasks(userId: String) {
        firestore.collection("tasks")
            .whereEqualTo("userId", userId)
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                val taskList = snapshot?.documents?.mapNotNull { doc -> 
                    doc.toObject(Task::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _tasks.value = taskList
            }
    }

    fun addTask(title: String, description: String = "", category: String = "Personal") {
        val userId = if (_isGuest.value) "guest_user_id" else _user.value?.uid ?: return
        val task = Task(
            title = title,
            description = description,
            category = category,
            order = System.currentTimeMillis().toInt() / 1000,
            userId = userId
        )
        firestore.collection("tasks").add(task)
    }

    fun toggleTaskCompletion(task: Task, context: Context? = null) {
        if (task.id.isEmpty()) {
            android.widget.Toast.makeText(context, "Error: Task ID is empty.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("tasks").document(task.id)
            .update("completed", !task.completed)
            .addOnFailureListener { e ->
                if (context != null) {
                    android.widget.Toast.makeText(context, "Failed to update: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
    }

    fun setTaskDueDate(task: Task, dueDate: Long, context: Context) {
        firestore.collection("tasks").document(task.id)
            .update("dueDate", dueDate)
            
        // Check for overlapping urgent tasks
        if (task.category.equals("Urgent", ignoreCase = true)) {
            val overlappingTasks = _tasks.value.filter {
                it.id != task.id &&
                it.category.equals("Urgent", ignoreCase = true) &&
                it.dueDate != null &&
                kotlin.math.abs(it.dueDate - dueDate) < 60 * 60 * 1000 // Within 1 hour
            }
            if (overlappingTasks.isNotEmpty()) {
                val overlapMsg = "Warning: Multiple urgent tasks scheduled around this time."
                // Append this warning to the chat log
                _chatLog.value = _chatLog.value + Content(parts = listOf(Part(text = overlapMsg)), role = "model")
                android.widget.Toast.makeText(context, overlapMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        scheduleNotification(context, task.title, task.description, dueDate, task.id.hashCode(), task.category)
    }

    private fun scheduleNotification(context: Context, title: String, message: String, timeInMillis: Long, requestCode: Int, category: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("category", category)
            putExtra("timeInMillis", timeInMillis)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteTask(task: Task) {
        firestore.collection("tasks").document(task.id).delete()
    }

    fun reorderTasks(fromIndex: Int, toIndex: Int) {
        val currentTasks = _tasks.value.toMutableList()
        val item = currentTasks.removeAt(fromIndex)
        currentTasks.add(toIndex, item)
        _tasks.value = currentTasks
        
        // Update order in firestore
        val batch = firestore.batch()
        currentTasks.forEachIndexed { index, task ->
            val docRef = firestore.collection("tasks").document(task.id)
            batch.update(docRef, "order", index)
        }
        batch.commit()
    }

    fun sendMessage(message: String, context: Context) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            
            var locationContext = "Location: Unknown."
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        locationContext = "User is currently at latitude: ${location.latitude}, longitude: ${location.longitude}."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val newUserMessage = Content(parts = listOf(Part(text = "[$locationContext]\n$message")), role = "user")
            val currentChat = _chatLog.value.toMutableList()
            currentChat.add(Content(parts = listOf(Part(text = message)), role = "user")) // Show without context block in UI
            _chatLog.value = currentChat

            val apiKey = BuildConfig.GEMINI_API_KEY

            val systemInstructionText = """
                You are an AI Concierge called The Last-Minute Life Saver. Help the user organize their life. 
                If the user wants to learn something, plan a project, or asks for auto-triage/goal alignment, break it down into actionable tasks and use the `addTask` tool.
                You can also schedule meetings (`scheduleMeeting`), create email drafts (`createDraftEmail`), and process payments (`sendPayment`).
                Do not output raw JSON for tasks. Always use the provided tools to interact with the app.
                When asked to schedule a meeting with a person and send them an email, use the scheduleMeeting tool first, get the meeting link from the response, and then use the createDraftEmail tool to draft an email with the link.
            """.trimIndent()

            val systemInstruction = Content(
                parts = listOf(Part(text = systemInstructionText))
            )

            val tools = listOf(
                buildJsonObject {
                    putJsonArray("functionDeclarations") {
                        addJsonObject {
                            put("name", "addTask")
                            put("description", "Add a new task to the user's to-do list")
                            putJsonObject("parameters") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("title") {
                                        put("type", "STRING")
                                        put("description", "The title of the task")
                                    }
                                    putJsonObject("description") {
                                        put("type", "STRING")
                                        put("description", "A brief description of the task")
                                    }
                                    putJsonObject("category") {
                                        put("type", "STRING")
                                        put("description", "The category (e.g. Work, Personal, Urgent)")
                                    }
                                }
                                putJsonArray("required") { add("title") }
                            }
                        }
                        addJsonObject {
                            put("name", "scheduleMeeting")
                            put("description", "Schedule a meeting, generate a meeting link, and invite attendees")
                            putJsonObject("parameters") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("title") { put("type", "STRING"); put("description", "Title of the meeting") }
                                    putJsonObject("time") { put("type", "STRING"); put("description", "Time of the meeting (e.g. 5pm tomorrow)") }
                                    putJsonObject("attendees") { 
                                        put("type", "ARRAY")
                                        putJsonObject("items") { put("type", "STRING") }
                                        put("description", "List of email addresses or names of attendees")
                                    }
                                }
                                putJsonArray("required") { add("title"); add("time") }
                            }
                        }
                        addJsonObject {
                            put("name", "createDraftEmail")
                            put("description", "Create a draft email to a recipient")
                            putJsonObject("parameters") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("recipient") { put("type", "STRING"); put("description", "Email address of the recipient") }
                                    putJsonObject("subject") { put("type", "STRING"); put("description", "Subject of the email") }
                                    putJsonObject("body") { put("type", "STRING"); put("description", "Body of the email") }
                                }
                                putJsonArray("required") { add("recipient"); add("subject"); add("body") }
                            }
                        }
                        addJsonObject {
                            put("name", "sendPayment")
                            put("description", "Send a payment to someone via a payment app")
                            putJsonObject("parameters") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("recipient") { put("type", "STRING"); put("description", "Name or handle of the recipient") }
                                    putJsonObject("amount") { put("type", "NUMBER"); put("description", "Amount to send") }
                                    putJsonObject("currency") { put("type", "STRING"); put("description", "Currency code (e.g. USD)") }
                                    putJsonObject("note") { put("type", "STRING"); put("description", "Note for the payment") }
                                }
                                putJsonArray("required") { add("recipient"); add("amount") }
                            }
                        }
                    }
                }
            )

            var currentApiChat = currentChat.dropLast(1) + newUserMessage
            var finalReplyText = ""

            try {
                var isDone = false
                while (!isDone) {
                    val request = GenerateContentRequest(
                        contents = currentApiChat,
                        systemInstruction = systemInstruction,
                        tools = tools
                    )
                    
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val firstCandidate = response.candidates?.firstOrNull()
                    val responseContent = firstCandidate?.content
                    val firstPart = responseContent?.parts?.firstOrNull()
                    
                    if (firstPart?.functionCall != null) {
                        val functionCall = firstPart.functionCall
                        val name = functionCall["name"]?.jsonPrimitive?.content
                        val args = functionCall["args"]?.jsonObject
                        
                        var resultObj: JsonObject = buildJsonObject { put("status", "unknown function") }
                        
                        if (name != null) {
                            if (_user.value != null || _isGuest.value) {
                                resultObj = McpIntegrationLayer.executeTool(
                                    name = name,
                                    args = args,
                                    context = context,
                                    onTaskCreated = { t, d, c -> addTask(t, d, c) }
                                )
                            } else {
                                resultObj = buildJsonObject { put("status", "error: not logged in") }
                            }
                        }
                        
                        // Add model's response to chat
                        currentApiChat = currentApiChat + (responseContent ?: Content(parts = listOf(firstPart), role = "model"))
                        
                        // Add our function response to chat
                        val functionResponsePart = Part(
                            functionResponse = buildJsonObject {
                                put("name", name ?: "")
                                putJsonObject("response") {
                                    put("result", resultObj)
                                }
                            }
                        )
                        currentApiChat = currentApiChat + Content(parts = listOf(functionResponsePart), role = "user")
                        
                    } else {
                        finalReplyText = firstPart?.text ?: "No response."
                        isDone = true
                    }
                }
                
                val newAssistantMessage = Content(parts = listOf(Part(text = finalReplyText)), role = "model")
                _chatLog.value = _chatLog.value + newAssistantMessage

            } catch (e: Exception) {
                _chatLog.value = _chatLog.value + Content(parts = listOf(Part(text = "Error: ${e.message}")), role = "model")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
