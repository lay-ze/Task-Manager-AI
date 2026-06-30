package com.example

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0,
    val isAllDay: Boolean = false
)
