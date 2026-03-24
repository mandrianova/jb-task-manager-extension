package com.taskmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.taskmanager.service.TaskStorageService
import com.taskmanager.service.TrackerType
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class TrackerSettingsDialog(private val project: Project) : DialogWrapper(project) {

    private val storageService = TaskStorageService.getInstance(project)
    private val currentConfig = storageService.loadTrackerConfig()

    private val typeCombo = ComboBox(TrackerType.entries.toTypedArray())
    private val baseUrlField = JBTextField(currentConfig.baseUrl, 30)
    private val hintLabel = JBLabel("")

    init {
        title = "Task Tracker Settings"
        init()

        typeCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ) = super.getListCellRendererComponent(list, (value as TrackerType).displayName, index, isSelected, cellHasFocus)
        }

        typeCombo.selectedItem = currentConfig.type
        updateHint()

        typeCombo.addActionListener {
            updateHint()
        }
    }

    private fun updateHint() {
        val selected = typeCombo.selectedItem as TrackerType
        hintLabel.text = when (selected) {
            TrackerType.NONE -> "No external tracker"
            TrackerType.LINEAR -> "e.g. https://linear.app/yourteam"
            TrackerType.JIRA -> "e.g. https://yourcompany.atlassian.net"
            TrackerType.GITHUB_ISSUES -> "e.g. https://github.com/owner/repo"
            TrackerType.YOUTRACK -> "e.g. https://youtrack.yourcompany.com"
        }
        baseUrlField.isEnabled = selected != TrackerType.NONE
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8)
        panel.preferredSize = Dimension(420, 140)

        // Tracker type
        val typePanel = JPanel(BorderLayout(8, 0))
        typePanel.add(JBLabel("Tracker:"), BorderLayout.WEST)
        typePanel.add(typeCombo, BorderLayout.CENTER)
        typePanel.maximumSize = Dimension(Int.MAX_VALUE, 32)
        panel.add(typePanel)
        panel.add(Box.createVerticalStrut(8))

        // Base URL
        val urlPanel = JPanel(BorderLayout(8, 0))
        urlPanel.add(JBLabel("Base URL:"), BorderLayout.WEST)
        urlPanel.add(baseUrlField, BorderLayout.CENTER)
        urlPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)
        panel.add(urlPanel)
        panel.add(Box.createVerticalStrut(4))

        // Hint
        hintLabel.font = hintLabel.font.deriveFont(11f)
        hintLabel.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
        hintLabel.border = JBUI.Borders.emptyLeft(70)
        panel.add(hintLabel)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val selected = typeCombo.selectedItem as TrackerType
        if (selected != TrackerType.NONE && baseUrlField.text.isBlank()) {
            return ValidationInfo("Base URL is required for ${selected.displayName}", baseUrlField)
        }
        return null
    }

    override fun doOKAction() {
        val selected = typeCombo.selectedItem as TrackerType
        storageService.saveTrackerConfig(
            com.taskmanager.service.TrackerConfig(
                type = selected,
                baseUrl = baseUrlField.text.trim()
            )
        )
        super.doOKAction()
    }
}
