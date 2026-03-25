package com.taskmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class TaskManagerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Tab 1: Tasks
        val tasksPanel = TaskManagerPanel(project)
        val tasksContent = contentFactory.createContent(tasksPanel, "Tasks", false)
        toolWindow.contentManager.addContent(tasksContent)

        // Tab 2: Commands & Skills
        val commandsPanel = ClaudeCommandsPanel(project)
        val commandsContent = contentFactory.createContent(commandsPanel, "Commands", false)
        toolWindow.contentManager.addContent(commandsContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
