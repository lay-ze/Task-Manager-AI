package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskModelTest {

    @Test
    fun `test task creation with default values`() {
        val task = Task(id = "1", title = "New Task")
        
        assertEquals("1", task.id)
        assertEquals("New Task", task.title)
        assertEquals("", task.description)
        assertEquals("Personal", task.category)
        assertEquals("Normal", task.priority)
        assertEquals("To Do", task.status)
        assertTrue(task.tags.isEmpty())
        assertTrue(task.subtasks.isEmpty())
        assertEquals(0, task.order)
        assertEquals(false, task.completed)
        assertEquals("", task.userId)
        assertNotNull(task.createdAt)
        assertNotNull(task.updatedAt)
        assertEquals(null, task.dueDate)
        assertEquals(false, task.isRecurring)
        assertEquals(null, task.recurrencePattern)
    }

    @Test
    fun `test task with advanced features`() {
        val subtask = Subtask(title = "Subtask 1")
        val task = Task(
            id = "2",
            title = "Advanced Task",
            priority = "High",
            status = "In Progress",
            tags = listOf("Work", "Urgent"),
            subtasks = listOf(subtask),
            isRecurring = true,
            recurrencePattern = "Daily"
        )
        
        assertEquals("High", task.priority)
        assertEquals("In Progress", task.status)
        assertEquals(2, task.tags.size)
        assertTrue(task.tags.contains("Work"))
        assertEquals(1, task.subtasks.size)
        assertEquals("Subtask 1", task.subtasks[0].title)
        assertEquals(false, task.subtasks[0].isCompleted)
        assertTrue(task.isRecurring)
        assertEquals("Daily", task.recurrencePattern)
    }
}
