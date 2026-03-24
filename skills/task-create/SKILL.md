---
name: task-create
description: Create new tasks or task groups in the project task manager. Creates entries in .claude/tasks/tasks.json and markdown docs in .claude/tasks/docs/. Use when asked to plan, create, or add tasks.
---

# Task Create

You create tasks in the project's task management system stored at `.claude/tasks/`.

## Input

Arguments via `$ARGUMENTS` in this format:
```
"Group Name" "Task Name" "Optional description"
```

Examples:
- `/task-create "Backend API" "Add authentication" "Implement JWT auth flow"`
- `/task-create "Refactoring" "Extract utils"`

If only a group name is provided, create the group without tasks.

## Steps

1. Ensure directories exist:
   - `.claude/tasks/`
   - `.claude/tasks/docs/`

2. Read `.claude/tasks/tasks.json` (create with `{"groups": []}` if missing).

3. Find or create the group:
   - If a group with the given name exists, use it.
   - Otherwise create a new group:
     ```json
     {
       "id": "<8-char-uuid>",
       "name": "<group name>",
       "order": <next sequential number>,
       "createdAt": "<ISO-8601 timestamp>",
       "tasks": []
     }
     ```

4. If a task name is provided, create the task:
   ```json
   {
     "id": "<8-char-uuid>",
     "name": "<task name>",
     "description": "<description or empty string>",
     "status": "new",
     "mdFile": "<groupId>/<taskId>.md",
     "createdAt": "<ISO-8601 timestamp>",
     "updatedAt": "<ISO-8601 timestamp>"
   }
   ```

5. Create the markdown file at `.claude/tasks/docs/<groupId>/<taskId>.md`:

   ```markdown
   # <task name>

   ## Description
   <description or "Add description here.">

   ## Instructions
   Add detailed instructions here.

   ## Acceptance Criteria
   - [ ] Define acceptance criteria
   ```

6. Add task to the group's `tasks` array and save `tasks.json` with pretty-print formatting.

## Output

Display a summary:
```
Created:
  Group: <name> (new/existing) [<groupId>]
  Task: <name> [<taskId>]
  MD file: .claude/tasks/docs/<groupId>/<taskId>.md
```

## External tracker integration

Check if `.claude/tasks/config.json` exists:

```json
{
  "type": "LINEAR",
  "baseUrl": "https://linear.app/yourteam"
}
```

Supported types: `LINEAR`, `JIRA`, `GITHUB_ISSUES`, `YOUTRACK`.

When a user mentions an external issue ID (e.g. "ENG-123", "PROJ-42", "#15"):
- Include the issue ID in the **group name** so the plugin can detect it and create a clickable link.
- Example group name: `"ENG-123 Implement authentication"`
- If the user provides just a tracker ID, ask what the task is about, or if context is clear, use the ID as a prefix.

## Auto-create issue in external tracker

After creating the task group locally, check if:

1. A tracker is configured in `.claude/tasks/config.json` (type is not `NONE`)
2. You have access to the corresponding MCP tool for that tracker:
   - **LINEAR**: Look for `save_issue` tool (Linear MCP). Create an issue with the group name as title and the task descriptions as body. Use the returned issue identifier (e.g. `ENG-123`) to **rename the group** so it includes the ID prefix.
   - **JIRA**: Look for Jira MCP tools (`create_issue` or similar). Same approach — create issue, prepend returned key to group name.
   - **GITHUB_ISSUES**: Look for GitHub MCP tools or use `gh issue create` CLI. Prepend `#<number>` to group name.
   - **YOUTRACK**: Look for YouTrack MCP tools or CLI.

3. If the MCP tool is NOT available, skip external creation silently — just inform the user: "Note: No MCP connection to {tracker} found. Issue was not created in the external tracker."

4. If the MCP tool IS available and issue creation succeeds, update the group name in `tasks.json` to include the external ID, e.g.:
   - Before: `"Implement authentication"`
   - After: `"ENG-123 Implement authentication"`

This way the plugin will automatically detect the ID and show a clickable link.

## Batch creation

If the user provides multiple tasks at once, create them all in the same group. Parse the input flexibly — the user might list tasks in natural language.

## Task JSON structure reference

```json
{
  "groups": [
    {
      "id": "abcd1234",
      "name": "Group Name",
      "order": 1,
      "createdAt": "2025-01-01T00:00:00Z",
      "tasks": [
        {
          "id": "efgh5678",
          "name": "Task Name",
          "description": "Brief description",
          "status": "new",
          "mdFile": "abcd1234/efgh5678.md",
          "createdAt": "2025-01-01T00:00:00Z",
          "updatedAt": "2025-01-01T00:00:00Z"
        }
      ]
    }
  ]
}
```
