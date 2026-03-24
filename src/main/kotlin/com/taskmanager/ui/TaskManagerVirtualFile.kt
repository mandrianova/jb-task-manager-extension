package com.taskmanager.ui

import com.intellij.testFramework.LightVirtualFile

class TaskManagerVirtualFile : LightVirtualFile("Task Manager") {
    override fun isWritable(): Boolean = false
    override fun isDirectory(): Boolean = false
    override fun isValid(): Boolean = true

    override fun equals(other: Any?): Boolean = other is TaskManagerVirtualFile
    override fun hashCode(): Int = "TaskManagerVirtualFile".hashCode()
}
