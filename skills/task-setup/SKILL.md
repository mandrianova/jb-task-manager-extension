---
name: task-setup
description: Set up Task Manager files for the current project. Initializes the configured task directory and installs task-cli.sh.
---

# Task Setup

Configure the current project for the Task Manager plugin and agent skills.

## What this does

1. Resolves the task directory and creates it with `tasks.json` if missing
2. Copies `task-cli.sh` if missing
3. For Claude Code, creates or updates `.claude/settings.local.json` with permissions for task management paths

## Steps

### 1. Resolve and initialize task directory

Resolve `TASKS_DIR`:
1. If `TASK_MANAGER_TASKS_DIR` is set, use that.
2. Else if `.claude/tasks` exists, use `.claude/tasks` for backward compatibility.
3. Else if `.agents/tasks` exists, use `.agents/tasks`.
4. Else if `.kiro/tasks` exists, use `.kiro/tasks`.
5. Else if `.idea/agents-tasks` exists, use `.idea/agents-tasks`.
6. Else if `TASK_MANAGER_AGENT=codex` or you are running in Codex, use `.agents/tasks`; if `TASK_MANAGER_AGENT=kiro-cli` or you are running in Kiro, use `.kiro/tasks`; otherwise use `.claude/tasks`.

```bash
mkdir -p "$TASKS_DIR/docs"
```

If `$TASKS_DIR/tasks.json` doesn't exist, create it:

```json
{"groups": []}
```

### 2. Install task-cli.sh

If `$TASKS_DIR/task-cli.sh` doesn't exist, check if it's available at:
- `scripts/task-cli.sh` (in current project, if this is the plugin repo)
- The global skills directory

If found, copy it and make executable. If not found, inform the user they need to install the Task Manager plugin skills first.

### 3. Configure Claude permissions when applicable

If you are running in Claude Code, read `.claude/settings.local.json` if it exists. Merge the following permissions into the existing `allow` array (don't replace existing rules):

```json
{
  "permissions": {
    "allow": [
      "Bash(bash .claude/tasks/task-cli.sh:*)",
      "Bash(bash .agents/tasks/task-cli.sh:*)",
      "Bash(bash .kiro/tasks/task-cli.sh:*)",
      "Bash(bash .idea/agents-tasks/task-cli.sh:*)",
      "Bash(git log:*)",
      "Bash(git rev-parse:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)",
      "Bash(git diff:*)",
      "Bash(git status:*)",
      "Read(.claude/tasks/**)",
      "Edit(.claude/tasks/**)",
      "Write(.claude/tasks/**)",
      "Read(.agents/tasks/**)",
      "Edit(.agents/tasks/**)",
      "Write(.agents/tasks/**)",
      "Read(.kiro/tasks/**)",
      "Edit(.kiro/tasks/**)",
      "Write(.kiro/tasks/**)",
      "Read(.idea/agents-tasks/**)",
      "Edit(.idea/agents-tasks/**)",
      "Write(.idea/agents-tasks/**)"
    ]
  }
}
```

If the file doesn't exist, create it with these permissions.

If it already exists, only add rules that are not already present.

**This file is gitignored** — it stays local to the machine.

If you are running in Codex, prefer the resolved `TASKS_DIR`; do not create another task directory when one already exists.

### 4. Verify .gitignore

Check that the selected local task directory is covered by `.gitignore`. If `.claude/`, `.agents/`, or `.idea/` is already ignored for the selected path, it's fine. If not, suggest adding the relevant path.

## Output

Show a summary:

```
Task Manager setup complete:
  ✓ <TASKS_DIR>/tasks.json
  ✓ <TASKS_DIR>/task-cli.sh
  ✓ .claude/settings.local.json (N permission rules, Claude only)

You can now use task-create and task-execute for task management operations.
```
