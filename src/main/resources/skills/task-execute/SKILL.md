---
name: task-execute
description: Execute tasks from the project task manager. Uses task-cli.sh to read tasks and update status. Performs the work described in task instructions with a structured workflow.
---

# Task Execute

You are executing tasks from the project's task management system.

## CLI helper

Use the `task-cli.sh` script for all task data operations instead of reading/writing JSON manually:

```bash
# List all groups and tasks
bash .claude/tasks/task-cli.sh list

# List tasks in a specific group
bash .claude/tasks/task-cli.sh list --group <groupId>

# Get full details of a task or group
bash .claude/tasks/task-cli.sh get <id>

# Update task status
bash .claude/tasks/task-cli.sh status <taskId> <status>

# Attach commit hash to a task
bash .claude/tasks/task-cli.sh commit <taskId> <commitHash>

# Create a new subtask
bash .claude/tasks/task-cli.sh add-task <groupId> "<name>" "<description>"

# Get tracker config
bash .claude/tasks/task-cli.sh config
```

If `task-cli.sh` is not found at `.claude/tasks/task-cli.sh`, check the project root at `scripts/task-cli.sh` and copy it:
```bash
cp scripts/task-cli.sh .claude/tasks/task-cli.sh
```

## Input

You receive a single argument via `$ARGUMENTS`: either a **group ID** or a **task ID** (8-character string).

## Finding the task

1. Run `bash .claude/tasks/task-cli.sh get <id>` to find the task or group.
2. If a **group ID** — run `bash .claude/tasks/task-cli.sh list --group <id>` to see all tasks, then process ones with status `new` or `in_progress` in order.
3. If a **task ID** — process that specific task.
4. Run `bash .claude/tasks/task-cli.sh config` to check for an external tracker.
5. If the ID is not found, run `bash .claude/tasks/task-cli.sh list` to show all available IDs.

## External tracker integration

If config shows a configured tracker (type is not `NONE`), and a group name contains a tracker issue ID (e.g. `ENG-123`, `#42`):

- Mention the external issue ID when summarizing the task.
- Include the issue ID in the commit message (e.g. `fix: implement auth flow (ENG-123)` or `Fixes #42`).

## Execution workflow

For each task, follow these stages strictly in order:

### Stage 1: Analysis & Planning

1. Read the markdown file from `.claude/tasks/docs/<mdFile>`.
2. Update status: `bash .claude/tasks/task-cli.sh status <taskId> in_progress`
3. Analyze the task requirements thoroughly.
4. If anything is unclear, **ask the user** for clarification.
5. Create a detailed implementation plan.
6. **Append the plan to the task's MD file** under a `## Plan` section.
7. Wait for the user to confirm the plan before moving to Stage 2.

### Stage 2: Implementation & Quality

1. Write the code according to the plan.
2. **Self-review**: Re-read your changes critically. Check for:
   - Bugs and logic errors
   - Security issues (injections, XSS, etc.)
   - Code style consistency with the existing codebase
   - Missing edge cases
3. **Run tests and linters**: Check what the project uses for quality checks:
   - Look for `.pre-commit-config.yaml`, `Makefile`, `package.json` scripts, `pyproject.toml`, `build.gradle.kts` etc.
   - Run the relevant test suite (e.g., `pytest`, `npm test`, `./gradlew test`)
   - Run the relevant linters/formatters (e.g., `ruff`, `eslint`, `ktlint`)
   - If pre-commit hooks are configured, run `pre-commit run --files <changed-files>` or equivalent
4. Fix any issues found by tests or linters.
5. If you had to make significant fixes, repeat the self-review.

### Stage 3: User feedback loop

1. Show the user a summary of all changes made.
2. Ask: "Are you happy with these changes? Should I commit, or do you want me to adjust something?"
3. If the user requests changes — go back to **Stage 2**, make adjustments, re-run checks.
4. Repeat until the user explicitly approves.

### Stage 4: Commit

1. **Learn the project's commit style** before committing:
   - Run `git log --oneline -20` to see recent commit messages.
   - Check for a `COMMIT_CONVENTION.md` file in the project root or `.claude/`.
   - Infer the style from recent commits if no convention file exists.
2. Stage only the files you changed: `git add <specific-files>`.
3. Commit with a message that follows the project's commit convention.
4. **Do NOT add any co-authorship lines** (no `Co-Authored-By` trailer).
5. If pre-commit hooks fail, fix the issues and create a new commit (do NOT amend).
6. **Record the commit hash**: After committing, get the hash with `git rev-parse HEAD` and run:
   ```bash
   bash .claude/tasks/task-cli.sh commit <taskId> $(git rev-parse HEAD)
   ```

### Stage 5: Completion

1. Update status: `bash .claude/tasks/task-cli.sh status <taskId> completed`
2. Append a `## Result` section to the task's MD file with a summary of what was done and the commit hash.

## Creating subtasks

If during execution you discover work that should be a separate task:

```bash
# Get the group ID from the current task
bash .claude/tasks/task-cli.sh add-task <groupId> "Subtask name" "Description of what needs to be done"
```

This creates the task entry and the markdown file automatically. Inform the user about the new subtask.

## Valid status values

- `new` — not started
- `in_progress` — currently being worked on
- `completed` — done
- `paused` — temporarily stopped
- `cancelled` — will not be done
