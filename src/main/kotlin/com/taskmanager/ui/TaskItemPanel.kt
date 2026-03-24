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
import javax.swing.*

class TaskItemPanel(
    private val project: Project,
    private val task: Task,
    private val onRunTask: (Task) -> Unit
) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(2, 24, 2, 4)
        maximumSize = Dimension(Int.MAX_VALUE, 32)

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        leftPanel.isOpaque = false

        // Status icon
        val statusIcon = JBLabel(getStatusIcon(task.status))
        statusIcon.toolTipText = task.status.displayName
        leftPanel.add(statusIcon)

        // Task name
        val nameLabel = JBLabel(task.name)
        nameLabel.toolTipText = task.description.ifBlank { task.name }
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
            nameLabel.foreground = UIUtil.getInactiveTextColor()
        }
        leftPanel.add(nameLabel)

        // MD file link
        val mdLink = HyperlinkLabel(task.mdFile.substringAfterLast('/'))
        mdLink.toolTipText = "Open ${task.mdFile}"
        mdLink.addHyperlinkListener {
            openMdFile()
        }
        leftPanel.add(mdLink)

        add(leftPanel, BorderLayout.CENTER)

        // Run button
        val runButton = JButton(AllIcons.Actions.Execute)
        runButton.toolTipText = "Run task with Claude"
        runButton.preferredSize = Dimension(28, 28)
        runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        runButton.isBorderPainted = false
        runButton.isContentAreaFilled = false
        runButton.addActionListener { onRunTask(task) }

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightPanel.isOpaque = false
        rightPanel.add(runButton)
        add(rightPanel, BorderLayout.EAST)

        isOpaque = false
    }

    private fun openMdFile() {
        val storageService = TaskStorageService.getInstance(project)
        val absPath = storageService.getMdAbsolutePath(task.mdFile)
        val virtualFile = VirtualFileManager.getInstance()
            .refreshAndFindFileByNioPath(absPath) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    private fun getStatusIcon(status: TaskStatus): Icon = when (status) {
        TaskStatus.NEW -> AllIcons.Actions.AddMulticaret
        TaskStatus.IN_PROGRESS -> AllIcons.Process.Step_1
        TaskStatus.COMPLETED -> AllIcons.Actions.Checked
        TaskStatus.PAUSED -> AllIcons.Actions.Pause
        TaskStatus.CANCELLED -> AllIcons.Actions.Cancel
    }
}
