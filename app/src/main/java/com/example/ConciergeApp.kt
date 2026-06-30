@file:Suppress("DEPRECATION")

package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Analytics
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun ConciergeApp(modifier: Modifier = Modifier, viewModel: ConciergeViewModel = viewModel(), initialAction: String? = null) {
    val user by viewModel.user.collectAsState()
    val isGuest by viewModel.isGuest.collectAsState()

    if (user == null && !isGuest) {
        LoginScreen(
            modifier = modifier,
            onSkip = { viewModel.setGuestMode(true) }
        )
    } else {
        MainScreen(
            modifier = modifier,
            viewModel = viewModel,
            initialAction = initialAction,
            onLogout = {
                if (isGuest) {
                    viewModel.setGuestMode(false)
                } else {
                    FirebaseAuth.getInstance().signOut()
                }
            }
        )
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onSkip: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var isSigningIn by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener {
                    isSigningIn = false
                    if (!it.isSuccessful) {
                        Toast.makeText(context, "Sign in failed: ${it.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: ApiException) {
                isSigningIn = false
                Toast.makeText(context, "Google Sign In Failed", Toast.LENGTH_SHORT).show()
            }
        } else {
            isSigningIn = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Psychology,
            contentDescription = "App Icon",
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Concierge",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "The Last-Minute Life Saver",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (webClientId.isBlank()) {
                    Toast.makeText(context, "Please configure GOOGLE_WEB_CLIENT_ID in secrets", Toast.LENGTH_LONG).show()
                    return@Button
                }
                isSigningIn = true
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .requestScopes(
                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar"),
                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar.events"),
                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/gmail.compose")
                    )
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                launcher.launch(googleSignInClient.signInIntent)
            },
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
            enabled = !isSigningIn
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Sign in with Google")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: ConciergeViewModel, initialAction: String? = null, onLogout: () -> Unit) {
    val tasks by viewModel.tasks.collectAsState()
    val chatLog by viewModel.chatLog.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    
    var inputText by remember { mutableStateOf("") }
    
    var currentScreen by remember { mutableStateOf("Chat") }
    var taskFilter by remember { mutableStateOf("All") } // "Today", "Upcoming", "All"
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskDesc by remember { mutableStateOf("") }
    var newTaskCat by remember { mutableStateOf("Personal") }
    var activeTextField by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialAction) {
        if (initialAction != null) {
            when (initialAction) {
                "OPEN_DASHBOARD" -> currentScreen = "Dashboard"
                "TRIGGER_AUTO_SCHEDULE" -> {
                    currentScreen = "Chat"
                    viewModel.triggerPanicMode()
                }
                "SUGGEST_BREAK" -> {
                    viewModel.addTask("Take a break", "Suggested by Daily Briefing", "Personal")
                    currentScreen = "Tasks"
                    Toast.makeText(context, "Break task added!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val text = results[0]
                when (activeTextField) {
                    "braindump" -> viewModel.processBrainDump(text, context)
                    "chat" -> inputText = if (inputText.isEmpty()) text else "$inputText $text"
                    "title" -> newTaskTitle = if (newTaskTitle.isEmpty()) text else "$newTaskTitle $text"
                    "desc" -> newTaskDesc = if (newTaskDesc.isEmpty()) text else "$newTaskDesc $text"
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(chatLog.size, isLoading) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0 && currentScreen == "Chat") {
            listState.animateScrollToItem(count - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(currentScreen) {
                    "Tasks" -> "Your Tasks"
                    "Dashboard" -> "Dashboard"
                    else -> "Concierge"
                }, fontWeight = FontWeight.Bold) },
                actions = {
                    if (currentScreen == "Tasks") {
                        var syncing by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                syncing = true
                                val result = CalendarHelper.autoScheduleTasks(context, tasks, viewModel)
                                android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                                syncing = false
                            }
                        }) {
                            if (syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.DateRange, contentDescription = "Sync to Calendar")
                            }
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Text("Logout", modifier = Modifier.padding(end = 8.dp))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = currentScreen == "Chat",
                    onClick = { currentScreen = "Chat" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    selected = currentScreen == "Tasks",
                    onClick = { currentScreen = "Tasks" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentScreen == "Dashboard",
                    onClick = { currentScreen = "Dashboard" }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen == "Tasks") {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            activeTextField = "braindump"
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = { Icon(Icons.Filled.SettingsVoice, contentDescription = "Brain Dump") },
                        text = { Text("Brain Dump") },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    FloatingActionButton(onClick = { showCreateTaskDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Task")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (currentScreen == "Tasks") {
            val now = java.util.Calendar.getInstance()
            val todayStart = now.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayEnd = todayStart + 24 * 60 * 60 * 1000 - 1

            val filteredTasks = tasks.filter { task ->
                when (taskFilter) {
                    "Today" -> !task.completed && task.dueDate != null && task.dueDate <= todayEnd
                    "Upcoming" -> !task.completed && task.dueDate != null && task.dueDate > todayEnd
                    "Done" -> task.completed
                    else -> !task.completed
                }
            }

            Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = taskFilter == "All",
                        onClick = { taskFilter = "All" },
                        label = { Text("All Tasks") }
                    )
                    FilterChip(
                        selected = taskFilter == "Today",
                        onClick = { taskFilter = "Today" },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = taskFilter == "Upcoming",
                        onClick = { taskFilter = "Upcoming" },
                        label = { Text("Upcoming") }
                    )
                    FilterChip(
                        selected = taskFilter == "Done",
                        onClick = { taskFilter = "Done" },
                        label = { Text("Done") }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (filteredTasks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "No tasks",
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (tasks.isEmpty()) "You're all caught up!" else "No tasks match this filter.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (tasks.isEmpty()) {
                                    Text(
                                        "Ask the Concierge to add a task for you, like 'Remind me to call John tomorrow' or 'Add a work task'.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            count = filteredTasks.size,
                            key = { index -> filteredTasks[index].id }
                        ) { index ->
                            val task = filteredTasks[index]
                            var isVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(task.id) {
                                isVisible = true
                            }
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                                    initialOffsetY = { 50 },
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                                ),
                                exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                                    targetOffsetY = { 50 },
                                    animationSpec = tween(200)
                                ),
                                modifier = Modifier.animateItem()
                            ) {
                                TaskItem(
                                    task = task,
                                    index = index,
                                    totalTasks = filteredTasks.size,
                                    onToggle = { viewModel.toggleTaskCompletion(it, context) },
                                    onDelete = { viewModel.deleteTask(it) },
                                    onSetReminder = { t, time -> viewModel.setTaskDueDate(t, time, context) },
                                    onMove = { from, to -> viewModel.reorderTasks(from, to) }
                                )
                            }
                        }
                    }
                }
            }
        } else if (currentScreen == "Chat") {
            Column(modifier = modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp)) {
                
                // Scrollable Content for Chat
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Text("Conversation", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(chatLog) { message ->
                        val isModel = message.role == "model"
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = if (isModel) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isModel) MaterialTheme.colorScheme.secondaryContainer 
                                                     else MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = message.parts.firstOrNull()?.text ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    color = if (isModel) MaterialTheme.colorScheme.onSecondaryContainer 
                                            else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                // Input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask me to do something...") },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                activeTextField = "chat"
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                }
                                try { speechLauncher.launch(intent) } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Filled.SettingsVoice, contentDescription = "Voice Input")
                            }
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.sendMessage(inputText, context)
                                        inputText = ""
                                    },
                                    modifier = Modifier.testTag("send_button")
                                ) {
                                    Icon(Icons.Filled.Send, contentDescription = "Send")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        } else if (currentScreen == "Dashboard") {
            val calendarEvents by viewModel.calendarEvents.collectAsState()
            val panicModeResult by viewModel.panicModeResult.collectAsState()
            val dailyBriefingResult by viewModel.dailyBriefing.collectAsState()
            LaunchedEffect(Unit) {
                viewModel.fetchCalendarEvents(context)
            }
            DashboardContent(
                modifier = modifier.padding(paddingValues), 
                tasks = tasks, 
                calendarEvents = calendarEvents,
                onPanicModeClick = { viewModel.triggerPanicMode() },
                onDailyBriefingClick = { viewModel.generateDailyBriefing() }
            )
            
            dailyBriefingResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearDailyBriefing() },
                    title = { Text("Daily Briefing") },
                    text = {
                        Column {
                            Text(result.pushNotificationText)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Suggested Action: ${result.suggestedAppAction}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearDailyBriefing() }) {
                            Text("OK")
                        }
                    }
                )
            }
            
            panicModeResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPanicModeResult() },
                    title = { Text("Panic Mode: Triage Complete") },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Text(result.comfortingMessage, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                            }
                            if (result.minimumViableActions.isNotEmpty()) {
                                item {
                                    Text("Minimum Viable Actions:", fontWeight = FontWeight.Bold)
                                }
                                items(result.minimumViableActions.size) { index ->
                                    Text("- ${result.minimumViableActions[index]}")
                                }
                            }
                            if (result.reschedule.isNotEmpty()) {
                                item {
                                    Text("To Reschedule:", fontWeight = FontWeight.Bold)
                                }
                                items(result.reschedule.size) { index ->
                                    val r = result.reschedule[index]
                                    Text("- Task: ${r.taskId}\n  New Time: ${r.suggestedNewTime}\n  Reason: ${r.reasoning}")
                                }
                            }
                            if (result.cancelOrDelegate.isNotEmpty()) {
                                item {
                                    Text("To Cancel/Delegate:", fontWeight = FontWeight.Bold)
                                }
                                items(result.cancelOrDelegate.size) { index ->
                                    val c = result.cancelOrDelegate[index]
                                    Text("- Task: ${c.taskId}\n  Draft: ${c.draftedMessage}")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearPanicModeResult() }) {
                            Text("Got it, thanks!")
                        }
                    }
                )
            }
        }
    }

    if (showCreateTaskDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTaskDialog = false },
            title = { Text("Create New Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                activeTextField = "title"
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                }
                                try { speechLauncher.launch(intent) } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Filled.SettingsVoice, contentDescription = "Voice Input")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = newTaskDesc,
                        onValueChange = { newTaskDesc = it },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(onClick = {
                                activeTextField = "desc"
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                }
                                try { speechLauncher.launch(intent) } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Filled.SettingsVoice, contentDescription = "Voice Input")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = newTaskCat,
                        onValueChange = { newTaskCat = it },
                        label = { Text("Category (e.g. Work, Urgent)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        viewModel.addTask(newTaskTitle, newTaskDesc, newTaskCat)
                        newTaskTitle = ""
                        newTaskDesc = ""
                        newTaskCat = "Personal"
                        showCreateTaskDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    newTaskTitle = ""
                    newTaskDesc = ""
                    newTaskCat = "Personal"
                    showCreateTaskDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TaskItem(
    task: Task, 
    index: Int,
    totalTasks: Int,
    onToggle: (Task) -> Unit, 
    onDelete: (Task) -> Unit, 
    onSetReminder: (Task, Long) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var draggedDistance by remember { mutableStateOf(0f) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset(y = draggedDistance.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggedDistance = 0f },
                    onDrag = { change, dragAmount -> 
                        change.consume()
                        draggedDistance += (dragAmount.y / density)
                        val threshold = 70f // approx height of an item
                        if (draggedDistance > threshold && index < totalTasks - 1) {
                             onMove(index, index + 1)
                             draggedDistance -= threshold
                        } else if (draggedDistance < -threshold && index > 0) {
                             onMove(index, index - 1)
                             draggedDistance += threshold
                        }
                    },
                    onDragEnd = { draggedDistance = 0f },
                    onDragCancel = { draggedDistance = 0f }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = if (draggedDistance != 0f) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { onToggle(task) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onToggle(task) }) {
                Icon(
                    imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "Toggle completion",
                    tint = if (task.completed) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.category.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (task.category.lowercase()) {
                            "work" -> MaterialTheme.colorScheme.primaryContainer
                            "urgent" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = task.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.category.lowercase()) {
                                "work" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "urgent" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }
            if (task.dueDate == null && !task.completed) {
                IconButton(onClick = { 
                    val time = System.currentTimeMillis() + 86400000 // 1 day for testing
                    onSetReminder(task, time) 
                }) {
                    Icon(Icons.Filled.Alarm, contentDescription = "Set Reminder (Tomorrow)", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = { onDelete(task) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

data class TimelineItem(val title: String, val time: Long, val isTask: Boolean)

@Composable
fun DashboardContent(modifier: Modifier = Modifier, tasks: List<Task>, calendarEvents: List<CalendarEvent> = emptyList(), onPanicModeClick: () -> Unit = {}, onDailyBriefingClick: () -> Unit = {}) {
    val activeTasks = tasks.count { !it.completed }
    val completedTasks = tasks.count { it.completed }
    val productivityScore = if (tasks.isEmpty()) 100 else (completedTasks * 100 / tasks.size)
    val now = System.currentTimeMillis()
    val overdueTasks = tasks.count { !it.completed && it.dueDate != null && it.dueDate < now }
    val upcomingTasks = tasks.filter { !it.completed && it.dueDate != null && it.dueDate > now }
    
    val timelineItems = mutableListOf<TimelineItem>()
    upcomingTasks.forEach { task ->
        timelineItems.add(TimelineItem(task.title, task.dueDate!!, true))
    }
    calendarEvents.forEach { event ->
        if (event.startMs > now) {
            timelineItems.add(TimelineItem(event.title, event.startMs, false))
        }
    }
    timelineItems.sortBy { it.time }

    val riskLevel = when {
        overdueTasks > 2 -> "High Risk"
        overdueTasks > 0 -> "Medium Risk"
        else -> "Low Risk"
    }
    val riskColor = when (riskLevel) {
        "High Risk" -> MaterialTheme.colorScheme.error
        "Medium Risk" -> Color(0xFFFFA500) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Productivity Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDailyBriefingClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Briefing")
                    }
                    Button(
                        onClick = onPanicModeClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Save Me")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$productivityScore%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Active Tasks: $activeTasks", style = MaterialTheme.typography.bodyLarge)
                        Text("Completed: $completedTasks", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        item {
            Text("Weekly Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Tasks Completed (Past 7 Days)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val chartEntryModel = remember(tasks) {
                        val map = IntArray(7) { 0 }
                        val now = System.currentTimeMillis()
                        val dayMillis = 24 * 60 * 60 * 1000L
                        tasks.filter { it.completed }.forEach { task ->
                            val diff = now - task.updatedAt
                            val dayIndex = (diff / dayMillis).toInt()
                            if (dayIndex in 0..6) {
                                map[6 - dayIndex]++
                            }
                        }
                        if (map.all { it == 0 }) {
                            entryModelOf(1f, 3f, 2f, 5f, 4f, 6f, completedTasks.toFloat())
                        } else {
                            val entries = map.mapIndexed { index, value -> entryOf(index, value) }
                            entryModelOf(entries)
                        }
                    }

                    Chart(
                        chart = lineChart(),
                        model = chartEntryModel,
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                        modifier = Modifier.height(200.dp).fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text("AI Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recommendation: Focus on your most urgent tasks to keep your productivity high. Consider breaking down large tasks into smaller steps.", 
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Deadline Risk: $riskLevel", color = riskColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (overdueTasks > 0) {
                        Text("You have $overdueTasks overdue task(s).", style = MaterialTheme.typography.bodySmall, color = riskColor)
                    }
                }
            }
        }

        item {
            Text("Today's Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (timelineItems.isEmpty()) {
            item {
                Text("No upcoming events or scheduled tasks.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(timelineItems.take(10)) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isTask) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (item.isTask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            Text(timeFormat.format(java.util.Date(item.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
