package com.example

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Task(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Personal",
    val priority: String = "Normal", // e.g., Low, Normal, High
    val status: String = "To Do", // e.g., To Do, In Progress, Done
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val order: Int = 0,
    @get:PropertyName("completed") @set:PropertyName("completed") var completed: Boolean = false,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null
)

data class Subtask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    var isCompleted: Boolean = false
)
