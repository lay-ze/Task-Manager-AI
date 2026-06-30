package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.Calendar

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val actionState = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        scheduleDailyBriefing()

        actionState.value = intent.getStringExtra("action")

        enableEdgeToEdge()
        setContent {
            val initialAction by actionState.collectAsState()
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConciergeApp(modifier = Modifier.padding(innerPadding), initialAction = initialAction)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        actionState.value = intent.getStringExtra("action")
    }

    private fun scheduleDailyBriefing() {
        // Calculate initial delay to 8 AM next morning
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        val delay = target.timeInMillis - now.timeInMillis
        
        val workRequest = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBriefingWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

