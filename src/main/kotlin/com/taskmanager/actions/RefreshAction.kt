package com.taskmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class RefreshAction : AnAction(
    "Refresh Tasks",
    "Reload tasks from disk",
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Task Manager") ?: return
        val content = toolWindow.contentManager.selectedContent ?: return
        val panel = content.component as? com.taskmanager.ui.TaskManagerPanel ?: return
        panel.refresh()
    }
}
