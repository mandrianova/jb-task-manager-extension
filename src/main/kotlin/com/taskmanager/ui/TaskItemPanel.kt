package com.taskmanager.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.taskmanager.model.Task
import com.taskmanager.model.TaskStatus
import com.taskmanager.service.TaskStorageService
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.*

class TaskItemPanel(
    private val project: Project,
    private val task: Task,
    private val onRunTask: (Task) -> Unit
) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(2, 24, 2, 4)
        maximumSize = Dimension(Int.MAX_VALUE, 48)

        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        leftPanel.isOpaque = false

        // Top row: status icon + name + md link
        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        topRow.isOpaque = false

        // Status icon
        val statusIcon = JBLabel(getStatusIcon(task.status))
        statusIcon.toolTipText = task.status.displayName
        topRow.add(statusIcon)

        // Task name
        val nameLabel = JBLabel(task.name)
        nameLabel.toolTipText = task.description.ifBlank { task.name }
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
            nameLabel.foreground = UIUtil.getInactiveTextColor()
        }
        topRow.add(nameLabel)

        // MD file link
        val mdLink = HyperlinkLabel(task.mdFile.substringAfterLast('/'))
        mdLink.toolTipText = "Open ${task.mdFile}"
        mdLink.addHyperlinkListener {
            openMdFile()
        }
        topRow.add(mdLink)

        leftPanel.add(topRow)

        // Bottom row: time info
        val timeText = buildTimeInfo(task.createdAt, task.updatedAt)
        if (timeText.isNotEmpty()) {
            val timeRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
            timeRow.isOpaque = false
            timeRow.border = JBUI.Borders.emptyLeft(22) // align with name

            val timeLabel = JBLabel(timeText)
            timeLabel.toolTipText = "createdAt=${task.createdAt} updatedAt=${task.updatedAt} now=${Instant.now()}"
            timeLabel.font = timeLabel.font.deriveFont(Font.PLAIN, 10f)
            timeLabel.foreground = UIUtil.getContextHelpForeground()
            timeRow.add(timeLabel)
            leftPanel.add(timeRow)
        }

        add(leftPanel, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightPanel.isOpaque = false

        // Commit diff button (only if commitId is set)
        if (task.commitId.isNotBlank()) {
            val commitButton = JButton(AllIcons.Actions.Diff)
            commitButton.toolTipText = "Show commit diff (${task.commitId.take(7)})"
            commitButton.preferredSize = Dimension(28, 28)
            commitButton.isBorderPainted = false
            commitButton.isContentAreaFilled = false
            commitButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            commitButton.addActionListener { showCommitDiff(task.commitId) }
            rightPanel.add(commitButton)
        }

        // Run button
        val runButton = JButton(AllIcons.Actions.Execute)
        runButton.toolTipText = "Run task with Claude"
        runButton.preferredSize = Dimension(28, 28)
        runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        runButton.isBorderPainted = false
        runButton.isContentAreaFilled = false
        runButton.addActionListener { onRunTask(task) }
        rightPanel.add(runButton)

        add(rightPanel, BorderLayout.EAST)

        isOpaque = false
    }

    private fun showCommitDiff(commitHash: String) {
        try {
            // Use VCS Log to navigate to the commit
            val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                .build()

            // Open Git log and search for the commit
            val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val vcsToolWindow = toolWindowManager.getToolWindow("Version Control")
                ?: toolWindowManager.getToolWindow("Git")

            if (vcsToolWindow != null) {
                vcsToolWindow.activate {
                    // Navigate to commit in VCS log by selecting the Log tab
                    // and filtering by hash — user can then see the diff
                    val content = vcsToolWindow.contentManager.contents
                        .firstOrNull { it.displayName?.contains("Log", ignoreCase = true) == true }
                    if (content != null) {
                        vcsToolWindow.contentManager.setSelectedContent(content)
                    }
                }
            }

            // Also copy hash to clipboard for easy use
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(commitHash), null)

            // Show notification
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Commit hash copied to clipboard: ${commitHash.take(7)}\n\n" +
                    "The Git Log panel is now open — paste the hash\n" +
                    "in the search field to find this commit.",
                "Commit: ${commitHash.take(7)}"
            )
        } catch (_: Exception) {
            // Fallback: just copy hash
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(commitHash), null)
        }
    }

    private fun openMdFile() {
        val storageService = TaskStorageService.getInstance(project)
        val absPath = storageService.getMdAbsolutePath(task.mdFile)
        val virtualFile = VirtualFileManager.getInstance()
            .refreshAndFindFileByNioPath(absPath) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    private fun buildTimeInfo(createdAt: String, updatedAt: String): String {
        return try {
            val now = Instant.now()
            val created = Instant.parse(createdAt)
            val updated = Instant.parse(updatedAt)

            val parts = mutableListOf<String>()
            parts.add("opened ${formatRelativeTime(created, now)}")

            // Show "updated" only if it's actually after createdAt and different
            if (updated.isAfter(created)) {
                parts.add("updated ${formatRelativeTime(updated, now)}")
            }

            parts.joinToString(" · ")
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatRelativeTime(instant: Instant, now: Instant): String {
        val minutes = ChronoUnit.MINUTES.between(instant, now)
        if (minutes < 0) return "just now" // future timestamp, treat as now
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            minutes < 43200 -> "${minutes / 10080}w ago"
            else -> "${minutes / 43200}mo ago"
        }
    }

    private fun getStatusIcon(status: TaskStatus): Icon = when (status) {
        TaskStatus.NEW -> AllIcons.Actions.AddMulticaret
        TaskStatus.IN_PROGRESS -> AllIcons.Process.Step_1
        TaskStatus.COMPLETED -> AllIcons.Actions.Checked
        TaskStatus.PAUSED -> AllIcons.Actions.Pause
        TaskStatus.CANCELLED -> AllIcons.Actions.Cancel
    }
}
