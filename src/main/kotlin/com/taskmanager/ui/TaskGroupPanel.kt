package com.taskmanager.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.taskmanager.model.Task
import com.taskmanager.model.TaskGroup
import com.taskmanager.model.TaskStatus
import com.taskmanager.service.TaskStorageService
import com.taskmanager.service.TrackerType
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class TaskGroupPanel(
    private val project: Project,
    private val group: TaskGroup,
    private val onRunGroup: (TaskGroup) -> Unit,
    private val onRunTask: (Task) -> Unit
) : JPanel() {

    private var collapsed: Boolean = group.isCompleted
    private val contentPanel: JPanel
    private val collapseIcon: JBLabel

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(2, 0)
        isOpaque = false

        // Header
        val header = JPanel(BorderLayout())
        header.maximumSize = Dimension(Int.MAX_VALUE, 32)
        header.border = JBUI.Borders.empty(2, 4)
        header.isOpaque = false

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        leftHeader.isOpaque = false

        // Collapse toggle
        collapseIcon = JBLabel(if (collapsed) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown)
        collapseIcon.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        collapseIcon.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                toggleCollapse()
            }
        })
        leftHeader.add(collapseIcon)

        // Group name + optional tracker link
        val trackerConfig = TaskStorageService.getInstance(project).loadTrackerConfig()
        val externalId = trackerConfig.type.extractId(group.name)

        val nameLabel = JBLabel(group.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
        if (group.isCompleted) {
            nameLabel.foreground = UIUtil.getInactiveTextColor()
        }
        leftHeader.add(nameLabel)

        if (externalId != null && trackerConfig.type != TrackerType.NONE && trackerConfig.baseUrl.isNotBlank()) {
            val url = trackerConfig.type.buildUrl(trackerConfig.baseUrl, externalId)
            val linkLabel = JBLabel("\uD83D\uDD17 $externalId")
            linkLabel.foreground = Color(0x589DF6)
            linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            linkLabel.font = linkLabel.font.deriveFont(Font.PLAIN, 11f)
            linkLabel.toolTipText = url
            linkLabel.border = JBUI.Borders.emptyLeft(6)
            linkLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    BrowserUtil.browse(url)
                }
            })
            leftHeader.add(linkLabel)
        }

        // Status badge
        val statusBadge = JBLabel(getStatusText(group.derivedStatus))
        statusBadge.font = statusBadge.font.deriveFont(Font.PLAIN, 11f)
        statusBadge.foreground = getStatusColor(group.derivedStatus)
        statusBadge.border = JBUI.Borders.emptyLeft(8)
        leftHeader.add(statusBadge)

        // Task count
        val countLabel = JBLabel("(${group.tasks.size})")
        countLabel.foreground = UIUtil.getInactiveTextColor()
        countLabel.font = countLabel.font.deriveFont(Font.PLAIN, 11f)
        countLabel.border = JBUI.Borders.emptyLeft(4)
        leftHeader.add(countLabel)

        // Created date
        val createdLabel = JBLabel(formatGroupDate(group.createdAt))
        createdLabel.foreground = UIUtil.getContextHelpForeground()
        createdLabel.font = createdLabel.font.deriveFont(Font.PLAIN, 10f)
        createdLabel.border = JBUI.Borders.emptyLeft(8)
        leftHeader.add(createdLabel)

        header.add(leftHeader, BorderLayout.CENTER)

        // Run group button
        val runButton = JButton(AllIcons.Actions.Execute)
        runButton.toolTipText = "Run all tasks in group with Claude"
        runButton.preferredSize = Dimension(28, 28)
        runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        runButton.isBorderPainted = false
        runButton.isContentAreaFilled = false
        runButton.addActionListener { onRunGroup(group) }

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightHeader.isOpaque = false
        rightHeader.add(runButton)
        header.add(rightHeader, BorderLayout.EAST)

        // Add separator below header
        header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        header.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.x < leftHeader.width) toggleCollapse()
            }
        })

        add(header)

        // Separator
        add(JSeparator().apply {
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        })

        // Content: task items
        contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.isOpaque = false
        contentPanel.border = JBUI.Borders.emptyLeft(8)

        for (task in group.tasks) {
            contentPanel.add(TaskItemPanel(project, task, onRunTask))
        }

        contentPanel.isVisible = !collapsed
        add(contentPanel)
    }

    private fun toggleCollapse() {
        collapsed = !collapsed
        contentPanel.isVisible = !collapsed
        collapseIcon.icon = if (collapsed) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown
        revalidate()
        repaint()
    }

    private fun getStatusText(status: TaskStatus): String = when (status) {
        TaskStatus.NEW -> "\u25CB New"
        TaskStatus.IN_PROGRESS -> "\u25D4 In Progress"
        TaskStatus.COMPLETED -> "\u2713 Completed"
        TaskStatus.PAUSED -> "\u23F8 Paused"
        TaskStatus.CANCELLED -> "\u2717 Cancelled"
    }

    private fun getStatusColor(status: TaskStatus): Color = when (status) {
        TaskStatus.NEW -> UIUtil.getLabelForeground()
        TaskStatus.IN_PROGRESS -> Color(0x3592C4)
        TaskStatus.COMPLETED -> Color(0x59A869)
        TaskStatus.PAUSED -> Color(0xD9A343)
        TaskStatus.CANCELLED -> Color(0xDB5860)
    }

    private fun formatGroupDate(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            ""
        }
    }
}
