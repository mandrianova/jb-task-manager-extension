package com.taskmanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    @SerialName("new") NEW,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
    @SerialName("paused") PAUSED,
    @SerialName("cancelled") CANCELLED;

    val displayName: String
        get() = when (this) {
            NEW -> "New"
            IN_PROGRESS -> "In Progress"
            COMPLETED -> "Completed"
            PAUSED -> "Paused"
            CANCELLED -> "Cancelled"
        }
}
