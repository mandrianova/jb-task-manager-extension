---
name: task-create
description: Create new tasks or task groups in the project task manager. Uses task-cli.sh for data operations. Use when asked to plan, create, or add tasks.
---

# Task Create

You create tasks in the project's task management system. The task directory can vary by project.

Resolve `TASKS_DIR` before running commands:
1. If `TASK_MANAGER_TASKS_DIR` is set, use that.
2. Else if `.claude/tasks` exists, use `.claude/tasks` for backward compatibility.
3. Else if `.agents/tasks` exists, use `.agents/tasks`.
4. Else if `.kiro/tasks` exists, use `.kiro/tasks`.
5. Else if `.idea/agents-tasks` exists, use `.idea/agents-tasks`.
6. Else if `TASK_MANAGER_AGENT=codex` or you are running in Codex, use `.agents/tasks`; if `TASK_MANAGER_AGENT=kiro-cli` or you are running in Kiro, use `.kiro/tasks`; otherwise use `.claude/tasks`.

## CLI helper

**IMPORTANT:** Always use a **relative path** for `task-cli.sh` — never an absolute path. This ensures permission rules match correctly.

```bash
TASKS_DIR="${TASK_MANAGER_TASKS_DIR:-$(case "${TASK_MANAGER_AGENT:-}" in codex) echo .agents/tasks ;; kiro|kiro-cli) echo .kiro/tasks ;; *) echo .claude/tasks ;; esac)}"

# List existing groups and active tasks (includes tracker config)
bash "$TASKS_DIR/task-cli.sh" list

# Create a new group (prints groupId)
bash "$TASKS_DIR/task-cli.sh" add-group "Group Name"

# Create a new task in a group (prints taskId, creates MD file)
bash "$TASKS_DIR/task-cli.sh" add-task <groupId> "Task Name" "Optional description"
```

**Note:** `list` outputs tracker config automatically — no need to call `config` separately.

If `task-cli.sh` is not found at `$TASKS_DIR/task-cli.sh`, copy it from `scripts/task-cli.sh`.

## Input

Arguments via `$ARGUMENTS` — parsed flexibly. Examples:
- `/task-create "Backend API" "Add authentication" "Implement JWT auth flow"`
- `/task-create "Refactoring" "Extract utils"`
- `/task-create` (no args — ask user interactively)

## Steps

1. Resolve `TASKS_DIR` using the rules above. Check if `task-cli.sh` exists at `$TASKS_DIR/task-cli.sh`. If not, look for it at `scripts/task-cli.sh` and copy it.

2. List existing groups: `bash "$TASKS_DIR/task-cli.sh" list`

3. Find or create the group:
   ```bash
   # If a group with this name exists, use its ID from the list output
   # Otherwise create a new one:
   GROUP_ID=$(bash "$TASKS_DIR/task-cli.sh" add-group "Group Name")
   ```

4. Create the task(s):
   ```bash
   TASK_ID=$(bash "$TASKS_DIR/task-cli.sh" add-task "$GROUP_ID" "Task Name" "Description")
   ```
   This automatically creates the markdown file at `$TASKS_DIR/docs/<groupId>/<taskId>.md`.

5. If the user wants to add details to the MD file, edit it after creation.

## Output

Display a summary:
```
Created:
  Group: <name> (new/existing) [<groupId>]
  Task: <name> [<taskId>]
  MD file: <TASKS_DIR>/docs/<groupId>/<taskId>.md
```

## External tracker integration

The `list` command shows tracker config at the top of its output. If a tracker is configured (type is not `NONE`):

When a user mentions an external issue ID (e.g. "ENG-123", "PROJ-42", "#15"):
- Include the issue ID in the **group name** so the plugin can detect it and create a clickable link.
- Example: `bash "$TASKS_DIR/task-cli.sh" add-group "ENG-123 Implement authentication"`

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
