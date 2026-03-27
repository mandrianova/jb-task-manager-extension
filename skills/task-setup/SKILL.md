---
name: task-setup
description: Set up Task Manager permissions and files for the current project. Creates .claude/settings.local.json with recommended permissions, initializes task directories, and installs task-cli.sh.
---

# Task Setup

Configure the current project for the Task Manager plugin and Claude skills.

## What this does

1. Creates `.claude/tasks/` directory with `tasks.json` if missing
2. Copies `task-cli.sh` if missing
3. Creates or updates `.claude/settings.local.json` with permissions for task management

## Steps

### 1. Initialize task directory

```bash
mkdir -p .claude/tasks/docs
```

If `.claude/tasks/tasks.json` doesn't exist, create it:

```json
{"groups": []}
```

### 2. Install task-cli.sh

If `.claude/tasks/task-cli.sh` doesn't exist, check if it's available at:
- `scripts/task-cli.sh` (in current project, if this is the plugin repo)
- The global skills directory

If found, copy it and make executable. If not found, inform the user they need to install the Task Manager plugin skills first.

### 3. Configure permissions

Read `.claude/settings.local.json` if it exists. Merge the following permissions into the existing `allow` array (don't replace existing rules):

```json
{
  "permissions": {
    "allow": [
      "Bash(bash .claude/tasks/task-cli.sh:*)",
      "Bash(git log:*)",
      "Bash(git rev-parse:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)",
      "Bash(git diff:*)",
      "Bash(git status:*)",
      "Read(.claude/tasks/**)",
      "Edit(.claude/tasks/**)",
      "Write(.claude/tasks/**)"
    ]
  }
}
```

If the file doesn't exist, create it with these permissions.

If it already exists, only add rules that are not already present.

**This file is gitignored** — it stays local to the machine.

### 4. Verify .gitignore

Check that `.claude/settings.local.json` is covered by `.gitignore`. If `.claude/` is already ignored, it's fine. If not, suggest adding it.

## Output

Show a summary:

```
Task Manager setup complete:
  ✓ .claude/tasks/tasks.json
  ✓ .claude/tasks/task-cli.sh
  ✓ .claude/settings.local.json (N permission rules)

You can now use /task-create and /task-execute without permission prompts
for task management operations.
```
