package com.example

import kotlinx.serialization.Serializable

@Serializable
data class PanicModeResult(
    val minimumViableActions: List<String> = emptyList(),
    val reschedule: List<PanicModeReschedule> = emptyList(),
    val cancelOrDelegate: List<PanicModeCancel> = emptyList(),
    val comfortingMessage: String = ""
)

@Serializable
data class PanicModeReschedule(
    val taskId: String,
    val suggestedNewTime: String,
    val reasoning: String
)

@Serializable
data class PanicModeCancel(
    val taskId: String,
    val draftedMessage: String
)
