package com.example

import kotlinx.serialization.Serializable

@Serializable
data class DailyBriefingResult(
    val pushNotificationText: String = "",
    val suggestedAppAction: String = "OPEN_DASHBOARD"
)
