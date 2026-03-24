package com.taskmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.taskmanager.ui.CreateTaskDialog

class CreateTaskAction : AnAction(
    "Create Task",
    "Create a new task or task group",
    AllIcons.General.Add
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = CreateTaskDialog(project)
        dialog.showAndGet()
    }
}
