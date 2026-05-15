package com.taskmanager.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.taskmanager.service.AgentType
import com.taskmanager.service.TaskStorageService
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object TerminalHelper {

    fun runAgentSkill(project: Project, skillName: String, argument: String, tabName: String) {
        val storageService = TaskStorageService.getInstance(project)
        val agent = storageService.loadTrackerConfig().agent
        val command = buildCommand(agent, skillName, argument, storageService.getTasksBasePathRelative())
        openTerminalAndRun(project, tabName, command)
    }

    fun getConfiguredAgent(project: Project): AgentType {
        return TaskStorageService.getInstance(project).loadTrackerConfig().agent
    }

    private fun buildCommand(agent: AgentType, skillName: String, argument: String, tasksDir: String): String {
        val envPrefix = "TASK_MANAGER_AGENT=${shellQuote(agent.executableName)} TASK_MANAGER_TASKS_DIR=${shellQuote(tasksDir)}"
        val agentCommand = when (agent) {
            AgentType.CLAUDE -> {
                val command = "/$skillName"
                if (argument.isBlank()) {
                    "${agent.executableName} $command"
                } else {
                    "${agent.executableName} $command ${shellQuote(argument)}"
                }
            }
            AgentType.CODEX, AgentType.KIRO -> {
                val prompt = if (argument.isBlank()) {
                    "Use the $skillName skill."
                } else {
                    "Use the $skillName skill with this argument: $argument"
                }
                when (agent) {
                    AgentType.KIRO -> "${agent.executableName} chat ${shellQuote(prompt)}"
                    else -> "${agent.executableName} ${shellQuote(prompt)}"
                }
            }
        }
        return "$envPrefix $agentCommand"
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
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
