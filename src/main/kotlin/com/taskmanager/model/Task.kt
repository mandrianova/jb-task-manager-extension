package com.taskmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val name: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.NEW,
    val mdFile: String,
    val createdAt: String,
    val updatedAt: String
)
