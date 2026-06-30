package com.example

import kotlinx.serialization.Serializable

@Serializable
data class BrainDumpTask(
    val title: String,
    val description: String = "",
    val category: String = "Personal",
    val deadlineDays: Int? = null
)

@Serializable
data class BrainDumpResponse(
    val tasks: List<BrainDumpTask>
)
