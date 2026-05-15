package com.taskmanager.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream

@Service(Service.Level.PROJECT)
class ClaudeSkillScanner(private val project: Project) {

    data class ClaudeCommand(
        val name: String,
        val description: String,
        val source: Source,
        val filePath: Path
    )

    enum class Source(val label: String) {
        PROJECT_COMMAND("Project commands"),
        PROJECT_SKILL("Project skills"),
        GLOBAL_COMMAND("Global commands"),
        GLOBAL_SKILL("Global skills"),
        BUILTIN("Built-in commands")
    }

    fun scan(): List<ClaudeCommand> {
        val agent = TaskStorageService.getInstance(project).loadTrackerConfig().agent
        return scan(agent)
    }

    fun scan(agent: AgentType): List<ClaudeCommand> {
        val result = mutableListOf<ClaudeCommand>()
        val basePath = project.basePath ?: return result

        when (agent) {
            AgentType.CLAUDE -> scanClaude(basePath, result)
            AgentType.CODEX -> scanCodex(basePath, result)
            AgentType.KIRO -> scanKiro(basePath, result)
        }

        return result
    }

    private fun scanClaude(basePath: String, result: MutableList<ClaudeCommand>) {
        scanCommands(Paths.get(basePath, ".claude", "commands"), Source.PROJECT_COMMAND, result)
        scanSkills(Paths.get(basePath, ".claude", "skills"), Source.PROJECT_SKILL, result)

        val home = System.getProperty("user.home")
        scanCommands(Paths.get(home, ".claude", "commands"), Source.GLOBAL_COMMAND, result)
        scanSkills(Paths.get(home, ".claude", "skills"), Source.GLOBAL_SKILL, result)

        addBuiltinCommands(result)
    }

    private fun scanCodex(basePath: String, result: MutableList<ClaudeCommand>) {
        scanSkills(Paths.get(basePath, ".agents", "skills"), Source.PROJECT_SKILL, result)

        val home = System.getProperty("user.home")
        scanSkills(Paths.get(home, ".agents", "skills"), Source.GLOBAL_SKILL, result)
        scanSkills(Paths.get(home, ".codex", "skills"), Source.GLOBAL_SKILL, result, maxDepth = 3)
    }

    private fun scanKiro(basePath: String, result: MutableList<ClaudeCommand>) {
        scanSkills(Paths.get(basePath, ".kiro", "skills"), Source.PROJECT_SKILL, result)

        val home = System.getProperty("user.home")
        scanSkills(Paths.get(home, ".kiro", "skills"), Source.GLOBAL_SKILL, result)
    }

    private fun addBuiltinCommands(result: MutableList<ClaudeCommand>) {
        val builtins = listOf(
            ClaudeCommand("simplify", "Review changed code for reuse, quality, and efficiency, then fix issues", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("init", "Initialize Claude settings for the current project", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("review", "Review code changes in the current branch", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("pr-comments", "Address PR review comments", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("memory", "View or edit Claude project memory (CLAUDE.md)", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("help", "Show available commands and help", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("clear", "Clear conversation context", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("compact", "Compact conversation to save context", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("cost", "Show token usage and cost for current session", Source.BUILTIN, Paths.get("")),
            ClaudeCommand("doctor", "Check Claude installation and environment health", Source.BUILTIN, Paths.get("")),
        )
        result.addAll(builtins)
    }

    private fun scanCommands(dir: Path, source: Source, result: MutableList<ClaudeCommand>) {
        if (!Files.isDirectory(dir)) return
        try {
            Files.list(dir).use { stream ->
                stream.filter { it.toString().endsWith(".md") && Files.isRegularFile(it) }
                    .forEach { file ->
                        val name = file.fileName.toString().removeSuffix(".md")
                        val description = extractDescription(file)
                        result.add(ClaudeCommand(name, description, source, file))
                    }
            }
        } catch (_: Exception) {}
    }

    private fun scanSkills(dir: Path, source: Source, result: MutableList<ClaudeCommand>, maxDepth: Int = 2) {
        if (!Files.isDirectory(dir)) return
        try {
            Files.find(dir, maxDepth, { path, attrs ->
                attrs.isRegularFile && path.fileName.toString() == "SKILL.md"
            }).use { stream: Stream<Path> ->
                stream.forEach { skillFile ->
                    val skillDir = skillFile.parent
                    if (skillDir != null) {
                        val name = skillDir.fileName.toString()
                        val description = extractSkillDescription(skillFile)
                        result.add(ClaudeCommand(name, description, source, skillFile))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractDescription(file: Path): String {
        return try {
            val lines = Files.readAllLines(file)
            // Look for frontmatter description
            val desc = extractFrontmatterField(lines, "description")
            if (desc.isNotBlank()) return desc
            // Fallback: first non-empty, non-heading line
            lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
                ?.trim()?.take(120) ?: ""
        } catch (_: Exception) { "" }
    }

    private fun extractSkillDescription(file: Path): String {
        return try {
            val lines = Files.readAllLines(file)
            extractFrontmatterField(lines, "description")
                .ifBlank {
                    // Fallback: first paragraph after heading
                    lines.dropWhile { !it.startsWith("# ") }
                        .drop(1)
                        .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                        ?.trim()?.take(120) ?: ""
                }
        } catch (_: Exception) { "" }
    }

    private fun extractFrontmatterField(lines: List<String>, field: String): String {
        if (lines.isEmpty() || lines[0].trim() != "---") return ""
        val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (endIdx < 0) return ""
        val frontmatter = lines.subList(1, endIdx + 1)
        val line = frontmatter.firstOrNull { it.startsWith("$field:") } ?: return ""
        return line.substringAfter("$field:").trim().removeSurrounding("\"").removeSurrounding("'")
    }

    companion object {
        fun getInstance(project: Project): ClaudeSkillScanner {
            return project.getService(ClaudeSkillScanner::class.java)
        }
    }
}
