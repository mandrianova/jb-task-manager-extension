package com.taskmanager.model

import kotlinx.serialization.Serializable
@Serializable
data class TaskGroup(
    val id: String,
    val name: String,
    val order: Int,
    val createdAt: String,
    val tasks: MutableList<Task> = mutableListOf()
) {
    val derivedStatus: TaskStatus
        get() = when {
            tasks.isEmpty() -> TaskStatus.NEW
            tasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED } ->
                TaskStatus.COMPLETED
            tasks.any { it.status == TaskStatus.IN_PROGRESS } -> TaskStatus.IN_PROGRESS
            tasks.any { it.status == TaskStatus.PAUSED } -> TaskStatus.PAUSED
            else -> TaskStatus.NEW
        }

    val isCompleted: Boolean
        get() = derivedStatus == TaskStatus.COMPLETED
}
