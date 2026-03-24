# Task Manager — PyCharm Plugin

Plugin for managing project tasks with Claude Code integration. Provides a UI for tracking task groups, statuses, and running tasks via Claude skills directly from the IDE.

## Requirements

- PyCharm Professional 2025.3
- JDK 21 (`brew install openjdk@21`)
- Claude Code CLI (for task execution)

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew buildPlugin
```

The plugin ZIP will be at `build/distributions/jb-task-manager-extension-1.0.0.zip`.

## Install in PyCharm

1. Open **PyCharm > Settings** (`Cmd + ,`)
2. Go to **Plugins**
3. Click the **gear icon** (top right) > **Install Plugin from Disk...**
4. Select the ZIP file from `build/distributions/`
5. Click **OK** and restart PyCharm

After restart, the **Task Manager** tab appears in the right side panel.

## Development mode

To run a sandboxed PyCharm instance with the plugin loaded (no install needed):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runIde
```

## Usage

- **Right panel**: Click "Task Manager" tab to open the tool window
- **Editor tab**: Tools > Open Task Manager (opens in center, like a file)
- **Bottom panel**: Drag the tool window tab to the bottom bar
- **Create tasks**: Click "+ New Group / Task" button
- **Run tasks**: Click the play button on a task or group — opens a terminal with `claude /task-execute <id>`
- **View details**: Click the `.md` link on any task to open its description file

### Claude Skills

Two global skills are installed at `~/.claude/skills/`:

- `/task-execute <id>` — executes a task or group, updates statuses
- `/task-create "Group" "Task" "Description"` — creates a new task with an MD file

### Data storage

Task data is stored per-project:

```
<project>/.claude/tasks/
├── tasks.json        # active groups and tasks
├── archive.json      # completed groups (auto-archived)
└── docs/             # markdown files with task details
    └── <groupId>/
        └── <taskId>.md
```
