package com.taskmanager.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.taskmanager.model.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class TaskStorageService(private val project: Project) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val tasksPath = getTasksFilePath().toString()
                    if (events.any { it.path == tasksPath }) {
                        notifyListeners()
                    }
                }
            }
        )
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun getTasksBasePath(): Path {
        val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
        val config = loadTrackerConfig()
        return getTasksBasePath(config)
    }

    private fun getTasksBasePath(config: TrackerConfig): Path {
        val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
        return when (config.taskStorage) {
            TaskStorageType.CLAUDE -> Paths.get(basePath, ".claude", "tasks")
            TaskStorageType.AGENTS -> Paths.get(basePath, ".agents", "tasks")
            TaskStorageType.KIRO -> Paths.get(basePath, ".kiro", "tasks")
            TaskStorageType.IDEA -> Paths.get(basePath, ".idea", "agents-tasks")
            TaskStorageType.AUTO -> resolveAutoTasksBasePath(basePath, config.agent)
        }
    }

    private fun resolveAutoTasksBasePath(basePath: String, agent: AgentType): Path {
        val legacyPath = Paths.get(basePath, ".claude", "tasks")
        if (Files.exists(legacyPath)) return legacyPath

        return when (agent) {
            AgentType.CLAUDE -> legacyPath
            AgentType.CODEX -> Paths.get(basePath, ".agents", "tasks")
            AgentType.KIRO -> Paths.get(basePath, ".kiro", "tasks")
        }
    }

    fun getTasksBasePathRelative(): String {
        val projectBasePath = project.basePath ?: return getTasksBasePath().toString()
        val basePath = Paths.get(projectBasePath)
        return basePath.relativize(getTasksBasePath()).toString()
    }

    private fun getTasksFilePath(): Path = getTasksBasePath().resolve("tasks.json")
    private fun getArchiveFilePath(): Path = getTasksBasePath().resolve("archive.json")
    private fun getDocsPath(): Path = getTasksBasePath().resolve("docs")
    private fun getConfigFilePath(): Path = getTasksBasePath().resolve("config.json")
    private fun getConfigFilePath(config: TrackerConfig): Path = getTasksBasePath(config).resolve("config.json")

    fun loadTrackerConfig(): TrackerConfig {
        val configPaths = getKnownConfigPaths()
            .filter { Files.exists(it) }
            .sortedByDescending {
                try {
                    Files.getLastModifiedTime(it).toMillis()
                } catch (_: Exception) {
                    0L
                }
            }

        for (path in configPaths) {
            val content = Files.readString(path)
            if (content.isBlank()) continue
            return try {
                json.decodeFromString<TrackerConfig>(content)
            } catch (_: Exception) {
                TrackerConfig()
            }
        }
        return TrackerConfig()
    }

    fun saveTrackerConfig(config: TrackerConfig) {
        ensureDirectories(config)
        val content = json.encodeToString(TrackerConfig.serializer(), config)
        Files.writeString(getConfigFilePath(config), content)
        refreshVfs()
        notifyListeners()
    }

    private fun getKnownConfigPaths(): List<Path> {
        val basePath = project.basePath ?: return emptyList()
        return listOf(
            Paths.get(basePath, ".claude", "tasks", "config.json"),
            Paths.get(basePath, ".agents", "tasks", "config.json"),
            Paths.get(basePath, ".kiro", "tasks", "config.json"),
            Paths.get(basePath, ".idea", "agents-tasks", "config.json")
        )
    }

    private fun ensureDirectories(config: TrackerConfig = loadTrackerConfig()) {
        val tasksBasePath = getTasksBasePath(config)
        Files.createDirectories(tasksBasePath)
        Files.createDirectories(tasksBasePath.resolve("docs"))
    }

    fun isInitialized(): Boolean = Files.exists(getTasksFilePath())

    fun initializeProject() {
        if (isInitialized()) return
        ensureDirectories()
        saveTasks(TaskData())
    }

    fun getSkillsSourceDir(): Path {
        // Skills bundled with the plugin JAR are extracted to a temp dir,
        // but we keep them in the repo under skills/ for distribution.
        // Find the plugin's installation path or use a known location.
        val pluginClassDir = this::class.java.protectionDomain?.codeSource?.location?.toURI()
        if (pluginClassDir != null) {
            val jarPath = Paths.get(pluginClassDir)
            // Navigate from classes dir to project root: build/classes/kotlin/main -> project root
            val projectRoot = jarPath.parent?.parent?.parent?.parent
            val skillsDir = projectRoot?.resolve("skills")
            if (skillsDir != null && Files.isDirectory(skillsDir)) {
                return skillsDir
            }
        }
        return Paths.get("") // fallback
    }

    fun installSkills(agent: AgentType = loadTrackerConfig().agent, overwrite: Boolean = false): Boolean {
        val targetDir = getSkillsInstallPath(agent) ?: return false
        val skills = mapOf(
            "task-execute" to getSkillContent("task-execute"),
            "task-create" to getSkillContent("task-create"),
            "task-setup" to getSkillContent("task-setup")
        )

        var changed = false
        for ((name, content) in skills) {
            if (content.isNotBlank()) {
                val skillDir = targetDir.resolve(name)
                Files.createDirectories(skillDir)
                val skillFile = skillDir.resolve("SKILL.md")
                if (!Files.exists(skillFile) || overwrite) {
                    Files.writeString(skillFile, content)
                    changed = true
                }
            }
        }

        // Keep task data and the CLI helper in the configured task directory.
        val cliScript = getResourceContent("/scripts/task-cli.sh")
        if (cliScript.isNotBlank()) {
            ensureDirectories()
            val cliTarget = getTasksBasePath().resolve("task-cli.sh")
            if (!Files.exists(cliTarget) || overwrite) {
                Files.writeString(cliTarget, cliScript)
                cliTarget.toFile().setExecutable(true)
                changed = true
            }
        }

        refreshVfs()
        return changed
    }

    fun areSkillsInstalled(agent: AgentType = loadTrackerConfig().agent): Boolean {
        val skillsDir = getSkillsInstallPath(agent) ?: return false
        return Files.exists(skillsDir.resolve("task-execute/SKILL.md"))
                && Files.exists(skillsDir.resolve("task-create/SKILL.md"))
                && Files.exists(skillsDir.resolve("task-setup/SKILL.md"))
                && Files.exists(getTasksBasePath().resolve("task-cli.sh"))
    }

    private fun getSkillsInstallPath(agent: AgentType): Path? {
        return when (agent) {
            AgentType.CLAUDE -> {
                val basePath = project.basePath ?: return null
                Paths.get(basePath, ".claude", "skills")
            }
            AgentType.CODEX -> Paths.get(System.getProperty("user.home"), ".agents", "skills")
            AgentType.KIRO -> Paths.get(System.getProperty("user.home"), ".kiro", "skills")
        }
    }

    private fun getSkillContent(skillName: String): String {
        return getResourceContent("/skills/$skillName/SKILL.md")
    }

    private fun getResourceContent(path: String): String {
        val stream = this::class.java.getResourceAsStream(path)
        return stream?.bufferedReader()?.readText() ?: ""
    }

    fun loadTasks(): TaskData {
        val path = getTasksFilePath()
        if (!Files.exists(path)) {
            return TaskData()
        }
        val content = Files.readString(path)
        if (content.isBlank()) return TaskData()
        return try {
            json.decodeFromString<TaskData>(content)
        } catch (e: Exception) {
            TaskData()
        }
    }

    fun saveTasks(data: TaskData) {
        ensureDirectories()
        val content = json.encodeToString(TaskData.serializer(), data)
        Files.writeString(getTasksFilePath(), content)
        refreshVfs()
    }

    fun loadArchive(): TaskData {
        val path = getArchiveFilePath()
        if (!Files.exists(path)) return TaskData()
        val content = Files.readString(path)
        if (content.isBlank()) return TaskData()
        return try {
            json.decodeFromString<TaskData>(content)
        } catch (e: Exception) {
            TaskData()
        }
    }

    private fun saveArchive(data: TaskData) {
        ensureDirectories()
        val content = json.encodeToString(TaskData.serializer(), data)
        Files.writeString(getArchiveFilePath(), content)
    }

    fun addGroup(name: String): TaskGroup {
        val data = loadTasks()
        val maxOrder = data.groups.maxOfOrNull { it.order } ?: 0
        val group = TaskGroup(
            id = shortUuid(),
            name = name,
            order = maxOrder + 1,
            createdAt = nowIso()
        )
        data.groups.add(group)
        saveTasks(data)
        return group
    }

    fun addTask(groupId: String, name: String, description: String): Task {
        val data = loadTasks()
        val group = data.groups.find { it.id == groupId }
            ?: throw IllegalArgumentException("Group not found: $groupId")

        val taskId = shortUuid()
        val mdRelativePath = "$groupId/$taskId.md"

        val task = Task(
            id = taskId,
            name = name,
            description = description,
            status = TaskStatus.NEW,
            mdFile = mdRelativePath,
            createdAt = nowIso(),
            updatedAt = nowIso()
        )

        group.tasks.add(task)
        saveTasks(data)
        createMdFile(mdRelativePath, name, description)
        return task
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val data = loadTasks()
        for (group in data.groups) {
            val taskIndex = group.tasks.indexOfFirst { it.id == taskId }
            if (taskIndex >= 0) {
                group.tasks[taskIndex] = group.tasks[taskIndex].copy(
                    status = status,
                    updatedAt = nowIso()
                )
                saveTasks(data)
                return
            }
        }
    }

    fun archiveCompletedGroups() {
        val data = loadTasks()
        val completed = data.groups.filter { it.isCompleted }
        if (completed.isEmpty()) return

        val archive = loadArchive()
        archive.groups.addAll(completed)
        saveArchive(archive)

        data.groups.removeAll(completed)
        saveTasks(data)
    }

    fun deleteGroup(groupId: String) {
        val data = loadTasks()
        data.groups.removeAll { it.id == groupId }
        saveTasks(data)
    }

    fun deleteTask(taskId: String) {
        val data = loadTasks()
        for (group in data.groups) {
            group.tasks.removeAll { it.id == taskId }
        }
        saveTasks(data)
    }

    fun getMdAbsolutePath(mdRelativePath: String): Path {
        return getDocsPath().resolve(mdRelativePath)
    }

    private fun createMdFile(mdRelativePath: String, taskName: String, description: String) {
        val path = getDocsPath().resolve(mdRelativePath)
        Files.createDirectories(path.parent)
        val content = buildString {
            appendLine("# $taskName")
            appendLine()
            appendLine("## Description")
            appendLine(description.ifBlank { "Add description here." })
            appendLine()
            appendLine("## Instructions")
            appendLine("Add detailed instructions here.")
            appendLine()
            appendLine("## Acceptance Criteria")
            appendLine("- [ ] Define acceptance criteria")
        }
        Files.writeString(path, content)
        refreshVfs()
    }

    private fun refreshVfs() {
        // Refresh only the tasks directory instead of the entire VFS
        // to avoid triggering full project reindexing
        val tasksDir = getTasksBasePath().toFile()
        LocalFileSystem.getInstance().refreshIoFiles(listOf(tasksDir), true, true, null)
    }

    private fun shortUuid(): String = UUID.randomUUID().toString().substring(0, 8)
    private fun nowIso(): String = Instant.now().toString()

    companion object {
        fun getInstance(project: Project): TaskStorageService {
            return project.getService(TaskStorageService::class.java)
        }
    }
}
