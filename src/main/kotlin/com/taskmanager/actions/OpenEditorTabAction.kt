package com.taskmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.taskmanager.ui.TaskManagerVirtualFile

class OpenEditorTabAction : AnAction(
    "Open Task Manager",
    "Open Task Manager in editor tab",
    AllIcons.Actions.MoveToWindow
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openEditorTab(project)
    }

    companion object {
        fun openEditorTab(project: Project) {
            val file = TaskManagerVirtualFile()
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}
