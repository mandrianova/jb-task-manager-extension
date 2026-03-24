package com.taskmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.taskmanager.service.TaskStorageService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class CreateTaskDialog(private val project: Project) : DialogWrapper(project) {

    private val storageService = TaskStorageService.getInstance(project)
    private val data = storageService.loadTasks()

    private val NEW_GROUP_OPTION = "— New Group —"

    private val groupCombo = ComboBox<String>()
    private val newGroupField = JBTextField()
    private val taskNameField = JBTextField()
    private val descriptionArea = JBTextArea(3, 30)

    init {
        title = "Create Task"
        init()

        // Populate groups
        groupCombo.addItem(NEW_GROUP_OPTION)
        for (group in data.groups) {
            groupCombo.addItem(group.name)
        }

        groupCombo.addActionListener {
            newGroupField.isVisible = groupCombo.selectedItem == NEW_GROUP_OPTION
            newGroupField.parent?.revalidate()
        }

        newGroupField.isVisible = true
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8)
        panel.preferredSize = Dimension(400, 280)

        // Group selector
        val groupPanel = JPanel(BorderLayout(8, 0))
        groupPanel.add(JBLabel("Group:"), BorderLayout.WEST)
        groupPanel.add(groupCombo, BorderLayout.CENTER)
        groupPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)
        panel.add(groupPanel)
        panel.add(Box.createVerticalStrut(8))

        // New group name
        val newGroupPanel = JPanel(BorderLayout(8, 0))
        newGroupPanel.add(JBLabel("Group name:"), BorderLayout.WEST)
        newGroupPanel.add(newGroupField, BorderLayout.CENTER)
        newGroupPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)
        panel.add(newGroupPanel)
        panel.add(Box.createVerticalStrut(8))

        // Task name
        val taskPanel = JPanel(BorderLayout(8, 0))
        taskPanel.add(JBLabel("Task name:"), BorderLayout.WEST)
        taskPanel.add(taskNameField, BorderLayout.CENTER)
        taskPanel.maximumSize = Dimension(Int.MAX_VALUE, 32)
        panel.add(taskPanel)
        panel.add(Box.createVerticalStrut(8))

        // Description
        val descPanel = JPanel(BorderLayout(8, 0))
        descPanel.add(JBLabel("Description:"), BorderLayout.NORTH)
        descriptionArea.lineWrap = true
        descriptionArea.wrapStyleWord = true
        descPanel.add(JScrollPane(descriptionArea), BorderLayout.CENTER)
        panel.add(descPanel)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (groupCombo.selectedItem == NEW_GROUP_OPTION && newGroupField.text.isBlank()) {
            return ValidationInfo("Group name is required", newGroupField)
        }
        if (taskNameField.text.isBlank()) {
            return ValidationInfo("Task name is required", taskNameField)
        }
        return null
    }

    override fun doOKAction() {
        val groupId: String
        val selectedGroup = groupCombo.selectedItem as String

        if (selectedGroup == NEW_GROUP_OPTION) {
            val newGroup = storageService.addGroup(newGroupField.text.trim())
            groupId = newGroup.id
        } else {
            groupId = data.groups.first { it.name == selectedGroup }.id
        }

        storageService.addTask(
            groupId = groupId,
            name = taskNameField.text.trim(),
            description = descriptionArea.text.trim()
        )

        super.doOKAction()
    }
}
