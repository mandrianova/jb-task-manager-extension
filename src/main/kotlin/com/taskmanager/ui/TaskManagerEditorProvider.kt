package com.taskmanager.ui

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class TaskManagerEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is TaskManagerVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return TaskManagerFileEditor(project)
    }

    override fun getEditorTypeId(): String = "TaskManagerEditor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class TaskManagerFileEditor(project: Project) : FileEditor {
    private val panel = TaskManagerPanel(project)
    private val userData = mutableMapOf<Key<*>, Any?>()

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Task Manager"
    override fun isValid(): Boolean = true
    override fun isModified(): Boolean = false
    override fun setState(state: FileEditorState) {}

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = TaskManagerVirtualFile()
    override fun dispose() {}

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getUserData(key: Key<T>): T? = userData[key] as? T

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        userData[key] = value
    }
}
