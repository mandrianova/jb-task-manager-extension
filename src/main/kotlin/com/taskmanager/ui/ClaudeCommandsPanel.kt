package com.taskmanager.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.taskmanager.actions.TerminalHelper
import com.taskmanager.service.ClaudeSkillScanner
import com.taskmanager.service.ClaudeSkillScanner.Source
import java.awt.*
import javax.swing.*

class ClaudeCommandsPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val scanner = ClaudeSkillScanner.getInstance(project)
    private val listContainer = JPanel()

    init {
        border = JBUI.Borders.empty()

        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)

        listContainer.layout = BoxLayout(listContainer, BoxLayout.Y_AXIS)
        listContainer.border = JBUI.Borders.empty(4)

        val scrollPane = JBScrollPane(listContainer)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = JBUI.Borders.empty()
        add(scrollPane, BorderLayout.CENTER)

        refresh()
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Rescan commands and skills", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) { refresh() }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ClaudeCommandsToolbar", actionGroup, true)
        toolbar.targetComponent = this
        return toolbar
    }

    fun refresh() {
        listContainer.removeAll()
        val commands = scanner.scan()

        if (commands.isEmpty()) {
            val emptyLabel = JBLabel("No commands or skills found in .claude/")
            emptyLabel.border = JBUI.Borders.empty(20)
            emptyLabel.foreground = JBColor.GRAY
            listContainer.add(emptyLabel)
        } else {
            // Group by source
            val grouped = commands.groupBy { it.source }
            val sourceOrder = listOf(Source.PROJECT_COMMAND, Source.PROJECT_SKILL, Source.GLOBAL_COMMAND, Source.GLOBAL_SKILL, Source.BUILTIN)

            for (source in sourceOrder) {
                val items = grouped[source] ?: continue
                listContainer.add(createSectionHeader(source.label, items.size))
                listContainer.add(JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) })

                for (cmd in items.sortedBy { it.name }) {
                    listContainer.add(createCommandRow(cmd))
                }
                listContainer.add(Box.createVerticalStrut(8))
            }
        }

        listContainer.revalidate()
        listContainer.repaint()
    }

    private fun createSectionHeader(title: String, count: Int): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        panel.isOpaque = false
        panel.maximumSize = Dimension(Int.MAX_VALUE, 28)

        val label = JBLabel(title)
        label.font = label.font.deriveFont(Font.BOLD, 12f)
        panel.add(label)

        val countLabel = JBLabel("($count)")
        countLabel.foreground = UIUtil.getInactiveTextColor()
        countLabel.font = countLabel.font.deriveFont(Font.PLAIN, 11f)
        panel.add(countLabel)

        return panel
    }

    private fun createCommandRow(cmd: ClaudeSkillScanner.ClaudeCommand): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(3, 12, 3, 4)
        panel.maximumSize = Dimension(Int.MAX_VALUE, 52)
        panel.isOpaque = false

        // Left: icon + name + description
        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        leftPanel.isOpaque = false

        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        topRow.isOpaque = false

        val icon = when (cmd.source) {
            Source.PROJECT_COMMAND, Source.GLOBAL_COMMAND -> AllIcons.Actions.Run_anything
            Source.PROJECT_SKILL, Source.GLOBAL_SKILL -> AllIcons.Nodes.Template
            Source.BUILTIN -> AllIcons.Actions.Lightning
        }
        topRow.add(JBLabel(icon))

        val nameLabel = JBLabel("/${cmd.name}")
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
        topRow.add(nameLabel)

        // Scope badge
        val scopeText = if (cmd.source == Source.PROJECT_COMMAND || cmd.source == Source.PROJECT_SKILL) "project" else "global"
        val scopeLabel = JBLabel(scopeText)
        scopeLabel.font = scopeLabel.font.deriveFont(Font.PLAIN, 10f)
        scopeLabel.foreground = if (scopeText == "project") Color(0x59A869) else UIUtil.getInactiveTextColor()
        scopeLabel.border = JBUI.Borders.emptyLeft(6)
        topRow.add(scopeLabel)

        leftPanel.add(topRow)

        if (cmd.description.isNotBlank()) {
            val descRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
            descRow.isOpaque = false
            descRow.border = JBUI.Borders.emptyLeft(22)

            val descLabel = JBLabel(cmd.description.take(100))
            descLabel.font = descLabel.font.deriveFont(Font.PLAIN, 11f)
            descLabel.foreground = UIUtil.getContextHelpForeground()
            descRow.add(descLabel)
            leftPanel.add(descRow)
        }

        panel.add(leftPanel, BorderLayout.CENTER)

        // Right: buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightPanel.isOpaque = false

        // Open file button (not for built-in commands)
        if (cmd.source != Source.BUILTIN) {
            val openButton = JButton(AllIcons.Actions.Preview)
            openButton.toolTipText = "Open source file"
            openButton.preferredSize = Dimension(28, 28)
            openButton.isBorderPainted = false
            openButton.isContentAreaFilled = false
            openButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            openButton.addActionListener {
                val vf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(cmd.filePath)
                if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
            rightPanel.add(openButton)
        }

        // Run button
        val runButton = JButton(AllIcons.Actions.Execute)
        runButton.toolTipText = "Run /${cmd.name} in Claude"
        runButton.preferredSize = Dimension(28, 28)
        runButton.isBorderPainted = false
        runButton.isContentAreaFilled = false
        runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        runButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                TerminalHelper.runClaudeSkill(project, cmd.name, "", "/${cmd.name}")
            }
        }
        rightPanel.add(runButton)

        panel.add(rightPanel, BorderLayout.EAST)
        return panel
    }

    companion object {
        fun getInstance(project: Project): ClaudeCommandsPanel {
            return ClaudeCommandsPanel(project)
        }
    }
}
