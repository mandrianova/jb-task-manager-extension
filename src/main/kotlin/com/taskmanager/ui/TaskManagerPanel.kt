package com.taskmanager.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.taskmanager.actions.TerminalHelper
import com.taskmanager.model.Task
import com.taskmanager.model.TaskGroup
import com.taskmanager.model.TaskStatus
import com.taskmanager.service.AgentType
import com.taskmanager.service.TrackerConfig
import com.taskmanager.service.TaskStorageService
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class TaskManagerPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val storageService = TaskStorageService.getInstance(project)
    private val groupsContainer = JPanel()
    private val paginationPanel: PaginationPanel
    private val emptyLabel = JBLabel("No tasks yet. Use + to create a task group.")
    private val agentStatusLabel = JBLabel("")
    private var allGroups: List<TaskGroup> = emptyList()

    init {
        border = JBUI.Borders.empty()

        // Toolbar
        val toolbar = createToolbar()
        val header = JPanel(BorderLayout())
        header.add(toolbar.component, BorderLayout.NORTH)
        agentStatusLabel.border = JBUI.Borders.empty(2, 8, 4, 8)
        agentStatusLabel.font = agentStatusLabel.font.deriveFont(Font.PLAIN, 11f)
        agentStatusLabel.foreground = UIUtil.getContextHelpForeground()
        header.add(agentStatusLabel, BorderLayout.SOUTH)
        add(header, BorderLayout.NORTH)

        // Groups container
        groupsContainer.layout = BoxLayout(groupsContainer, BoxLayout.Y_AXIS)
        groupsContainer.border = JBUI.Borders.empty(4)

        val scrollPane = JBScrollPane(groupsContainer)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = JBUI.Borders.empty()
        add(scrollPane, BorderLayout.CENTER)

        // Pagination
        paginationPanel = PaginationPanel { _, _ -> renderGroups() }
        add(paginationPanel, BorderLayout.SOUTH)

        // Listen for external changes
        storageService.addChangeListener {
            SwingUtilities.invokeLater { refresh() }
        }

        refresh()
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(createSimpleAction("Refresh", AllIcons.Actions.Refresh) { refresh() })
            add(createAgentSelectorAction())
            add(createSimpleAction("Create Task with Agent", AllIcons.General.Add) {
                ApplicationManager.getApplication().invokeLater {
                    TerminalHelper.runAgentSkill(project, "task-create", "", "Create Task")
                }
            })
            addSeparator()
            add(createSimpleAction("Open in Editor", AllIcons.Actions.MoveToWindow) {
                com.taskmanager.actions.OpenEditorTabAction.openEditorTab(project)
            })
            add(createSimpleAction("Tracker Settings", AllIcons.General.GearPlain) {
                TrackerSettingsDialog(project).show()
            })
            add(createSimpleAction("Setup Permissions", AllIcons.Nodes.SecurityRole) {
                ApplicationManager.getApplication().invokeLater {
                    TerminalHelper.runAgentSkill(project, "task-setup", "", "Setup Permissions")
                }
            })
            add(object : AnAction("Install / Update Agent Skills", "Install or update task skills for the configured agent and task-cli.sh", AllIcons.Nodes.CopyOfFolder) {
                override fun actionPerformed(e: AnActionEvent) {
                    val agent = storageService.loadTrackerConfig().agent
                    val alreadyInstalled = storageService.areSkillsInstalled(agent)
                    if (alreadyInstalled) {
                        val choice = Messages.showYesNoDialog(
                            project,
                            "${agent.displayName} skills are already installed. Overwrite with the latest version from the plugin?",
                            "Update Skills",
                            "Update",
                            "Cancel",
                            AllIcons.General.QuestionDialog
                        )
                        if (choice != Messages.YES) return
                    }

                    val changed = storageService.installSkills(agent, overwrite = alreadyInstalled)
                    if (changed) {
                        val verb = if (alreadyInstalled) "updated" else "installed"
                        Messages.showInfoMessage(
                            project,
                            "Skills $verb in ${agent.skillsPathLabel}/\n\n" +
                                "• task-execute\n• task-create\n• task-setup\n\n" +
                                "task-cli.sh is installed in ${storageService.getTasksBasePathRelative()}/.",
                            "Skills ${verb.replaceFirstChar { it.uppercase() }}"
                        )
                    } else if (!alreadyInstalled) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to install skills. Check that the plugin resources are intact.",
                            "Installation Failed"
                        )
                    } else {
                        Messages.showInfoMessage(
                            project,
                            "Skills are already up to date.",
                            "No Changes"
                        )
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.icon = if (storageService.areSkillsInstalled())
                        AllIcons.General.InspectionsOK
                    else
                        AllIcons.Nodes.CopyOfFolder
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("TaskManagerToolbar", actionGroup, true)
        toolbar.targetComponent = this
        return toolbar
    }

    private fun createAgentSelectorAction(): AnAction {
        return object : DefaultActionGroup("Agent", true) {
            init {
                templatePresentation.icon = AllIcons.General.Settings
            }

            override fun update(e: AnActionEvent) {
                val agent = storageService.loadTrackerConfig().agent
                e.presentation.text = agent.displayName
                e.presentation.description = "Choose agent for task actions"
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return AgentType.entries.map { agent ->
                    object : AnAction(agent.displayName) {
                        override fun actionPerformed(e: AnActionEvent) {
                            val current = storageService.loadTrackerConfig()
                            if (current.agent == agent) return
                            storageService.saveTrackerConfig(
                                TrackerConfig(
                                    type = current.type,
                                    baseUrl = current.baseUrl,
                                    agent = agent,
                                    taskStorage = current.taskStorage
                                )
                            )
                        }

                        override fun update(e: AnActionEvent) {
                            val currentAgent = storageService.loadTrackerConfig().agent
                            e.presentation.icon = if (currentAgent == agent) {
                                AllIcons.General.InspectionsOK
                            } else {
                                null
                            }
                        }

                        override fun getActionUpdateThread() = ActionUpdateThread.BGT
                    }
                }.toTypedArray()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
    }

    private fun createSimpleAction(text: String, icon: javax.swing.Icon, action: () -> Unit): com.intellij.openapi.actionSystem.AnAction {
        return object : com.intellij.openapi.actionSystem.AnAction(text, text, icon) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                action()
            }
        }
    }

    fun refresh() {
        updateAgentStatus()
        val data = storageService.loadTasks()
        // Sort: active groups first, then newest by createdAt (with order as fallback)
        allGroups = data.groups.sortedWith(
            compareBy<TaskGroup> { it.isCompleted }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.order }
        )
        paginationPanel.update(allGroups.size)
        renderGroups()
    }

    private fun updateAgentStatus() {
        val config = storageService.loadTrackerConfig()
        agentStatusLabel.text = "Agent: ${config.agent.displayName} | Tasks: ${storageService.getTasksBasePathRelative()}"
        agentStatusLabel.toolTipText = "Current task agent and storage path"
    }

    private fun renderGroups() {
        groupsContainer.removeAll()

        if (allGroups.isEmpty()) {
            emptyLabel.border = JBUI.Borders.empty(20)
            emptyLabel.foreground = JBColor.GRAY
            groupsContainer.add(emptyLabel)
        } else {
            val start = paginationPanel.currentPage * paginationPanel.pageSize
            val end = minOf(start + paginationPanel.pageSize, allGroups.size)
            val pageGroups = allGroups.subList(start, end)

            for (group in pageGroups) {
                groupsContainer.add(
                    TaskGroupPanel(
                        project = project,
                        group = group,
                        onRunGroup = { g -> runGroup(g) },
                        onRunTask = { t -> runTask(t) },
                        onStatusChange = { t, status -> changeTaskStatus(t, status) }
                    )
                )
            }
        }

        groupsContainer.revalidate()
        groupsContainer.repaint()
    }

    private fun runGroup(group: TaskGroup) {
        ApplicationManager.getApplication().invokeLater {
            TerminalHelper.runAgentSkill(project, "task-execute", group.id, "Group: ${group.name}")
        }
    }

    private fun runTask(task: Task) {
        ApplicationManager.getApplication().invokeLater {
            TerminalHelper.runAgentSkill(project, "task-execute", task.id, "Task: ${task.name}")
        }
    }

    private fun changeTaskStatus(task: Task, status: TaskStatus) {
        val storageService = TaskStorageService.getInstance(project)
        storageService.updateTaskStatus(task.id, status)
        refresh()
    }
}
