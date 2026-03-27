---
name: task-create
description: Create new tasks or task groups in the project task manager. Uses task-cli.sh for data operations. Use when asked to plan, create, or add tasks.
---

# Task Create

You create tasks in the project's task management system stored at `.claude/tasks/`.

## CLI helper

**IMPORTANT:** Always use the **relative path** `bash .claude/tasks/task-cli.sh` — never an absolute path. This ensures permission rules match correctly.

```bash
# List existing groups and active tasks (includes tracker config)
bash .claude/tasks/task-cli.sh list

# Create a new group (prints groupId)
bash .claude/tasks/task-cli.sh add-group "Group Name"

# Create a new task in a group (prints taskId, creates MD file)
bash .claude/tasks/task-cli.sh add-task <groupId> "Task Name" "Optional description"
```

**Note:** `list` outputs tracker config automatically — no need to call `config` separately.

If `task-cli.sh` is not found at `.claude/tasks/task-cli.sh`, copy it from `scripts/task-cli.sh`.

## Input

Arguments via `$ARGUMENTS` — parsed flexibly. Examples:
- `/task-create "Backend API" "Add authentication" "Implement JWT auth flow"`
- `/task-create "Refactoring" "Extract utils"`
- `/task-create` (no args — ask user interactively)

## Steps

1. Check if `task-cli.sh` exists at `.claude/tasks/task-cli.sh`. If not, look for it at `scripts/task-cli.sh` and copy it.

2. List existing groups: `bash .claude/tasks/task-cli.sh list`

3. Find or create the group:
   ```bash
   # If a group with this name exists, use its ID from the list output
   # Otherwise create a new one:
   GROUP_ID=$(bash .claude/tasks/task-cli.sh add-group "Group Name")
   ```

4. Create the task(s):
   ```bash
   TASK_ID=$(bash .claude/tasks/task-cli.sh add-task "$GROUP_ID" "Task Name" "Description")
   ```
   This automatically creates the markdown file at `.claude/tasks/docs/<groupId>/<taskId>.md`.

5. If the user wants to add details to the MD file, edit it after creation.

## Output

Display a summary:
```
Created:
  Group: <name> (new/existing) [<groupId>]
  Task: <name> [<taskId>]
  MD file: .claude/tasks/docs/<groupId>/<taskId>.md
```

## External tracker integration

The `list` command shows tracker config at the top of its output. If a tracker is configured (type is not `NONE`):

When a user mentions an external issue ID (e.g. "ENG-123", "PROJ-42", "#15"):
- Include the issue ID in the **group name** so the plugin can detect it and create a clickable link.
- Example: `bash .claude/tasks/task-cli.sh add-group "ENG-123 Implement authentication"`

## Auto-create issue in external tracker

After creating the task group locally, check if:

1. A tracker is configured (type is not `NONE`)
2. You have access to the corresponding MCP tool:
   - **LINEAR**: Use `save_issue` tool. Prepend returned ID to group name.
   - **JIRA**: Use Jira MCP tools. Prepend returned key to group name.
   - **GITHUB_ISSUES**: Use `gh issue create` CLI. Prepend `#<number>`.
   - **YOUTRACK**: Use YouTrack MCP tools or CLI.

3. If no MCP tool available, inform the user and continue without external creation.

## Batch creation

If the user provides multiple tasks at once, create them all in the same group. Parse the input flexibly — the user might list tasks in natural language.
