package com.taskmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskData(
    val groups: MutableList<TaskGroup> = mutableListOf()
)
