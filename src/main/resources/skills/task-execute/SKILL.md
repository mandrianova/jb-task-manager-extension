---
name: task-execute
description: Execute tasks from the project task manager. Reads .claude/tasks/tasks.json, loads associated markdown docs, and performs the work described in task instructions. Updates task status as work progresses.
---

# Task Execute

You are executing tasks from the project's task management system.

## Input

You receive a single argument via `$ARGUMENTS`: either a **group ID** or a **task ID** (8-character string).

## Finding the task

1. Read the file `.claude/tasks/tasks.json` in the current project directory.
2. Read `.claude/tasks/config.json` to check if an external tracker is configured.
3. Parse the JSON and find the matching group or task by ID.
4. If a **group ID** is provided — find all tasks in that group with status `"new"` or `"in_progress"`, process them in order.
5. If a **task ID** is provided — find the specific task across all groups.
6. If the ID is not found, list all available group and task IDs so the user can correct the input.

## External tracker integration

If `.claude/tasks/config.json` exists and has a configured tracker:

```json
{
  "type": "LINEAR",
  "baseUrl": "https://linear.app/yourteam"
}
```

Supported types: `LINEAR`, `JIRA`, `GITHUB_ISSUES`, `YOUTRACK`.

When a group name contains a tracker issue ID (e.g. `"ENG-123 Implement auth"` for Linear/Jira, `"#42 Fix bug"` for GitHub Issues):

- Mention the external issue ID when summarizing the task.
- If changes are related to the external issue, include the issue ID in the commit message (e.g. `fix: implement auth flow (ENG-123)` or `Fixes #42`).
- When creating subtasks, consider whether they should reference the same external issue ID or a different one.

## Execution workflow

For each task, follow these stages strictly in order:

### Stage 1: Analysis & Planning

1. Read the markdown file from `.claude/tasks/docs/<mdFile>`.
2. Update the task status to `"in_progress"` in `tasks.json`.
3. Analyze the task requirements thoroughly — understand the codebase context, constraints, and goals.
4. If anything is unclear or ambiguous, **ask the user** for clarification before proceeding.
5. Create a detailed implementation plan.
6. **Append the plan to the task's MD file** under a `## Plan` section so it's documented.
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
   - If pre-commit hooks are configured, run `pre-commit run --files <changed-files>` or the equivalent
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
   - Check for a `COMMIT_CONVENTION.md` or similar file in `.claude/` or project root.
   - If no convention file exists, infer the style from recent commits (conventional commits, imperative mood, etc.).
2. Stage only the files you changed: `git add <specific-files>`.
3. Commit with a message that follows the project's commit convention.
4. **Do NOT add any co-authorship lines** (no `Co-Authored-By` trailer).
5. If pre-commit hooks fail, fix the issues and create a new commit (do NOT amend).

### Stage 5: Completion

1. Update the task status to `"completed"` in `tasks.json`.
2. Update the `"updatedAt"` field to the current ISO-8601 timestamp.
3. Append a `## Result` section to the task's MD file with a brief summary of what was done and the commit hash.

## Creating subtasks

If during execution you discover work that should be a separate task:

1. Generate an 8-character UUID for the new task ID.
2. Create a `.md` file at `.claude/tasks/docs/<groupId>/<newTaskId>.md` with the task details.
3. Add the task to the same group in `tasks.json` with status `"new"`.
4. Set `createdAt` and `updatedAt` to the current ISO-8601 timestamp.
5. Set `mdFile` to `"<groupId>/<newTaskId>.md"`.
6. Inform the user about the new subtask.

## Updating tasks.json

Read the current `tasks.json`, modify the relevant fields, and write the entire file back with pretty-print JSON formatting. Valid status values:

- `"new"` — not started
- `"in_progress"` — currently being worked on
- `"completed"` — done
- `"paused"` — temporarily stopped
- `"cancelled"` — will not be done

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
