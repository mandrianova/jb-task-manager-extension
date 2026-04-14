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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.*

class TaskItemPanel(
    private val project: Project,
    private val task: Task,
    private val onRunTask: (Task) -> Unit,
    private val onStatusChange: (Task, TaskStatus) -> Unit
) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(2, 24, 2, 4)
        maximumSize = Dimension(Int.MAX_VALUE, 48)

        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        leftPanel.isOpaque = false
        leftPanel.alignmentX = LEFT_ALIGNMENT

        // Top row: [icon] [name...truncates] [md link]
        val topRow = JPanel(BorderLayout(6, 0))
        topRow.isOpaque = false
        topRow.alignmentX = LEFT_ALIGNMENT
        topRow.maximumSize = Dimension(Int.MAX_VALUE, 22)

        // Status icon (fixed WEST)
        val statusIcon = JBLabel(getStatusIcon(task.status))
        statusIcon.toolTipText = task.status.displayName
        topRow.add(statusIcon, BorderLayout.WEST)

        // Task name (CENTER — truncates when narrow)
        val nameLabel = JBLabel(task.name)
        nameLabel.toolTipText = task.description.ifBlank { task.name }
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
            nameLabel.foreground = UIUtil.getInactiveTextColor()
        }
        topRow.add(nameLabel, BorderLayout.CENTER)

        // MD file link (fixed EAST — always visible)
        val mdLink = HyperlinkLabel(task.mdFile.substringAfterLast('/'))
        mdLink.toolTipText = "Open ${task.mdFile}"
        mdLink.addHyperlinkListener { openMdFile() }
        topRow.add(mdLink, BorderLayout.EAST)

        leftPanel.add(topRow)

        // Bottom row: time info
        val timeText = buildTimeInfo(task.createdAt, task.updatedAt)
        if (timeText.isNotEmpty()) {
            val timeRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
            timeRow.isOpaque = false
            timeRow.alignmentX = LEFT_ALIGNMENT
            timeRow.maximumSize = Dimension(Int.MAX_VALUE, 18)
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

        // Copy ID button (always shown)
        val copyButton = JButton(AllIcons.Actions.Copy)
        copyButton.toolTipText = "Copy task ID: ${task.id}"
        copyButton.preferredSize = Dimension(28, 28)
        copyButton.isBorderPainted = false
        copyButton.isContentAreaFilled = false
        copyButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        copyButton.addActionListener {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(task.id), null)
        }
        rightPanel.add(copyButton)

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

        // Action buttons depend on status
        when (task.status) {
            TaskStatus.IN_PROGRESS -> {
                // Complete button
                val completeButton = JButton(AllIcons.Actions.Checked)
                completeButton.toolTipText = "Mark as completed"
                completeButton.preferredSize = Dimension(28, 28)
                completeButton.isBorderPainted = false
                completeButton.isContentAreaFilled = false
                completeButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                completeButton.addActionListener { onStatusChange(task, TaskStatus.COMPLETED) }
                rightPanel.add(completeButton)

                // Cancel button
                val cancelButton = JButton(AllIcons.Actions.Suspend)
                cancelButton.toolTipText = "Cancel task"
                cancelButton.preferredSize = Dimension(28, 28)
                cancelButton.isBorderPainted = false
                cancelButton.isContentAreaFilled = false
                cancelButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                cancelButton.addActionListener { onStatusChange(task, TaskStatus.CANCELLED) }
                rightPanel.add(cancelButton)
            }
            TaskStatus.NEW, TaskStatus.PAUSED -> {
                // Run button
                val runButton = JButton(AllIcons.Actions.Execute)
                runButton.toolTipText = "Run task with Claude"
                runButton.preferredSize = Dimension(28, 28)
                runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                runButton.isBorderPainted = false
                runButton.isContentAreaFilled = false
                runButton.addActionListener { onRunTask(task) }
                rightPanel.add(runButton)
            }
            TaskStatus.COMPLETED, TaskStatus.CANCELLED -> {
                // No action buttons for finished tasks
            }
        }

        add(rightPanel, BorderLayout.EAST)

        isOpaque = false
    }

    private fun showCommitDiff(commitHash: String) {
        try {
            val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val vcsToolWindow = toolWindowManager.getToolWindow("Version Control")
                ?: toolWindowManager.getToolWindow("Git")

            if (vcsToolWindow != null) {
                vcsToolWindow.activate {
                    val content = vcsToolWindow.contentManager.contents
                        .firstOrNull { it.displayName?.contains("Log", ignoreCase = true) == true }
                    if (content != null) {
                        vcsToolWindow.contentManager.setSelectedContent(content)
                    }
                }
            }

            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(commitHash), null)

            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Commit hash copied to clipboard: ${commitHash.take(7)}\n\n" +
                    "The Git Log panel is now open — paste the hash\n" +
                    "in the search field to find this commit.",
                "Commit: ${commitHash.take(7)}"
            )
        } catch (_: Exception) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(commitHash), null)
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
        if (minutes < 0) return "just now"
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
