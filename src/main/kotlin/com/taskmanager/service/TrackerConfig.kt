package com.taskmanager.service

import kotlinx.serialization.Serializable

@Serializable
data class TrackerConfig(
    val type: TrackerType = TrackerType.NONE,
    val baseUrl: String = "",
    val agent: AgentType = AgentType.CLAUDE,
    val taskStorage: TaskStorageType = TaskStorageType.AUTO
)

@Serializable
enum class AgentType(
    val displayName: String,
    val executableName: String,
    val skillsPathLabel: String
) {
    CLAUDE("Claude Code", "claude", ".claude/skills"),
    CODEX("Codex", "codex", "~/.agents/skills"),
    KIRO("Kiro", "kiro-cli", "~/.kiro/skills")
}

@Serializable
enum class TaskStorageType(val displayName: String) {
    AUTO("Auto"),
    CLAUDE(".claude/tasks"),
    AGENTS(".agents/tasks"),
    KIRO(".kiro/tasks"),
    IDEA(".idea/agents-tasks")
}

@Serializable
enum class TrackerType(val displayName: String, val idPattern: String, val urlTemplate: String) {
    NONE("None", "", ""),
    LINEAR("Linear", """[A-Z]+-\d+""", "{baseUrl}/issue/{id}"),
    JIRA("Jira", """[A-Z]+-\d+""", "{baseUrl}/browse/{id}"),
    GITHUB_ISSUES("GitHub Issues", """#\d+""", "{baseUrl}/issues/{id}"),
    YOUTRACK("YouTrack", """[A-Z]+-\d+""", "{baseUrl}/issue/{id}");

    fun buildUrl(baseUrl: String, issueId: String): String {
        val cleanId = issueId.removePrefix("#")
        return urlTemplate
            .replace("{baseUrl}", baseUrl.trimEnd('/'))
            .replace("{id}", cleanId)
    }

    fun extractId(text: String): String? {
        if (this == NONE || idPattern.isEmpty()) return null
        val regex = Regex(idPattern)
        return regex.find(text)?.value
    }
}
