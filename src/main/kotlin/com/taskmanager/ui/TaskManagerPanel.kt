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
import com.taskmanager.actions.TerminalHelper
import com.taskmanager.model.Task
import com.taskmanager.model.TaskGroup
import com.taskmanager.service.TaskStorageService
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class TaskManagerPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val storageService = TaskStorageService.getInstance(project)
    private val groupsContainer = JPanel()
    private val paginationPanel: PaginationPanel
    private val emptyLabel = JBLabel("No tasks yet. Use + to create a task group.")
    private var allGroups: List<TaskGroup> = emptyList()

    init {
        border = JBUI.Borders.empty()

        // Auto-initialize project files on first open
        if (!storageService.isInitialized()) {
            storageService.initializeProject()
        }

        // Toolbar
        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)

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
            add(createSimpleAction("Create Task with Claude", AllIcons.General.Add) {
                ApplicationManager.getApplication().invokeLater {
                    TerminalHelper.runClaudeSkill(project, "task-create", "", "Create Task")
                }
            })
            addSeparator()
            add(createSimpleAction("Open in Editor", AllIcons.Actions.MoveToWindow) {
                com.taskmanager.actions.OpenEditorTabAction.openEditorTab(project)
            })
            add(createSimpleAction("Tracker Settings", AllIcons.General.GearPlain) {
                TrackerSettingsDialog(project).show()
            })
            add(object : AnAction("Install Claude Skills", "Install task-execute and task-create skills into this project", AllIcons.Nodes.CopyOfFolder) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (storageService.areSkillsInstalled()) {
                        Messages.showInfoMessage(
                            project,
                            "Claude skills are already installed in .claude/skills/",
                            "Skills Installed"
                        )
                    } else {
                        val installed = storageService.installSkills()
                        if (installed) {
                            Messages.showInfoMessage(
                                project,
                                "Skills installed to .claude/skills/\n\n" +
                                    "• task-execute — run tasks from the task manager\n" +
                                    "• task-create — create new tasks via Claude\n\n" +
                                    "You can now use /task-execute and /task-create in Claude.",
                                "Skills Installed"
                            )
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "Failed to install skills. Check that the plugin resources are intact.",
                                "Installation Failed"
                            )
                        }
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

    private fun createSimpleAction(text: String, icon: javax.swing.Icon, action: () -> Unit): com.intellij.openapi.actionSystem.AnAction {
        return object : com.intellij.openapi.actionSystem.AnAction(text, text, icon) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                action()
            }
        }
    }

    fun refresh() {
        val data = storageService.loadTasks()
        // Sort: active groups first, completed at the bottom
        allGroups = data.groups.sortedWith(
            compareBy<TaskGroup> { it.isCompleted }
                .thenBy { it.order }
        )
        paginationPanel.update(allGroups.size)
        renderGroups()
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
                        onRunTask = { t -> runTask(t) }
                    )
                )
            }
        }

        // Add create button at the bottom
        val createPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        createPanel.isOpaque = false
        val createButton = javax.swing.JButton("+ New Group / Task")
        createButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                TerminalHelper.runClaudeSkill(project, "task-create", "", "Create Task")
            }
        }
        createPanel.add(createButton)
        groupsContainer.add(createPanel)

        groupsContainer.revalidate()
        groupsContainer.repaint()
    }

    private fun runGroup(group: TaskGroup) {
        ApplicationManager.getApplication().invokeLater {
            TerminalHelper.runClaudeSkill(project, "task-execute", group.id, "Group: ${group.name}")
        }
    }

    private fun runTask(task: Task) {
        ApplicationManager.getApplication().invokeLater {
            TerminalHelper.runClaudeSkill(project, "task-execute", task.id, "Task: ${task.name}")
        }
    }
}
