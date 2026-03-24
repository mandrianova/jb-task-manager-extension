package com.taskmanager.service

import kotlinx.serialization.Serializable

@Serializable
data class TrackerConfig(
    val type: TrackerType = TrackerType.NONE,
    val baseUrl: String = ""
)

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
