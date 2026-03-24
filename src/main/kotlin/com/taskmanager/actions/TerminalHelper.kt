package com.taskmanager.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object TerminalHelper {

    fun runClaudeSkill(project: Project, skillName: String, argument: String, tabName: String) {
        val command = if (argument.isBlank()) "claude /$skillName" else "claude /$skillName $argument"
        openTerminalAndRun(project, tabName, command)
    }

    @Suppress("UnstableApiUsage")
    private fun openTerminalAndRun(project: Project, tabName: String, command: String) {
        ApplicationManager.getApplication().invokeLater {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Terminal")

            if (toolWindow == null) {
                runViaProcess(project, command)
                return@invokeLater
            }

            toolWindow.activate {
                try {
                    val manager = TerminalToolWindowManager.getInstance(project)
                    val widget = manager.createLocalShellWidget(
                        project.basePath ?: ".",
                        tabName
                    )
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(800)
                        ApplicationManager.getApplication().invokeLater {
                            widget.executeCommand(command)
                        }
                    }
                } catch (e: Exception) {
                    runViaProcess(project, command)
                }
            }
        }
    }

    private fun runViaProcess(project: Project, command: String) {
        try {
            val pb = ProcessBuilder("bash", "-c", command)
            pb.directory(java.io.File(project.basePath ?: "."))
            pb.inheritIO()
            pb.start()
        } catch (_: Exception) {}
    }
}
