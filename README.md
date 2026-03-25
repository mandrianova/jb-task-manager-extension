# Task Manager — JetBrains Plugin

A task management plugin for JetBrains IDEs (PyCharm, IntelliJ IDEA, WebStorm, etc.) with [Claude Code](https://docs.anthropic.com/en/docs/claude-code) integration. Manage project tasks from the IDE and execute them via Claude skills in the terminal.

> **Note:** This plugin is not published to the JetBrains Marketplace. Install it manually from a ZIP file (see below).

## Features

- **Task groups** with collapsible lists and automatic status tracking
- **Task statuses:** New, In Progress, Completed, Paused, Cancelled
- **Markdown docs:** Each task links to a `.md` file with detailed description, plan, and results
- **Claude integration:** Run tasks via `/task-execute` and create them via `/task-create` skills
- **External tracker links:** Detects Linear, Jira, GitHub Issues, and YouTrack IDs in group names and renders clickable links
- **Auto-create issues:** When creating tasks, can automatically create issues in external trackers via MCP
- **Smart ordering:** Completed groups sink to the bottom, appear faded, and auto-collapse
- **Pagination** with configurable page size
- **Commands tab:** Browse all Claude commands and skills (project, global, built-in) and run them with one click
- **Multiple views:** Side panel, bottom panel, or editor tab (center area)
- **Auto-archiving** of completed groups to keep `tasks.json` compact

## Requirements

- JetBrains IDE 2025.3+ (PyCharm, IntelliJ IDEA, WebStorm, etc.)
- JDK 21 for building (`brew install openjdk@21` on macOS)
- [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) for task execution

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Install the plugin

1. Build the plugin (see above) or download a release ZIP
2. Open your JetBrains IDE > **Settings** (`Cmd+,` / `Ctrl+Alt+S`)
3. Go to **Plugins**
4. Click the **gear icon** (top right) > **Install Plugin from Disk...**
5. Select the `.zip` file from `build/distributions/`
6. Click **OK** and **restart** the IDE

After restart, the **Task Manager** tab appears in the right side panel.

## Install Claude skills

The plugin relies on two Claude Code skills for creating and executing tasks.

### Option A: Install from the plugin UI (per-project)

1. Open the **Task Manager** panel in the IDE
2. Click the **Install Claude Skills** button (folder icon) in the toolbar
3. Skills are copied to `<project>/.claude/skills/` — the button icon changes to ✅ when done

### Option B: Install globally (all projects)

```bash
mkdir -p ~/.claude/skills/task-execute ~/.claude/skills/task-create
cp skills/task-execute/SKILL.md ~/.claude/skills/task-execute/SKILL.md
cp skills/task-create/SKILL.md ~/.claude/skills/task-create/SKILL.md
```

After installing, the skills are available in Claude:

- **`/task-execute <id>`** — Executes a task or group by ID. Follows a structured workflow: analyze → plan → implement → review → test → get feedback → commit → update status.
- **`/task-create "Group" "Task" "Description"`** — Creates a new task group and/or task. Generates a markdown file with description, instructions, and acceptance criteria templates.

## Usage

### UI

| Action | How |
|--------|-----|
| Open panel | Click **Task Manager** tab in the right sidebar |
| Open as editor tab | **Tools > Open Task Manager** or click the window icon in the toolbar |
| Move to bottom | Drag the tool window tab to the bottom bar |
| Create task | Click **+** in toolbar or **+ New Group / Task** at the bottom — opens Claude with `/task-create` |
| Run task | Click the ▶ play button on a task or group — opens Claude with `/task-execute` |
| View details | Click the 📄 link on a task to open its markdown file |
| Refresh | Click 🔄 in toolbar |
| Configure tracker | Click ⚙ in toolbar |
| Browse commands | Switch to the **Commands** tab in the tool window |
| Run a command | Click ▶ on any command/skill in the Commands tab |
| View command source | Click 👁 to open the `.md` file in the editor |

### External tracker integration

1. Click the ⚙ gear icon in the toolbar
2. Select your tracker type (Linear, Jira, GitHub Issues, YouTrack)
3. Enter the base URL (e.g. `https://linear.app/yourteam`)
4. Click OK

When a group name contains a tracker ID (e.g. `ENG-123 Implement auth`), a clickable 🔗 link appears next to it. Clicking opens the issue in your browser.

If a Linear MCP server is connected to Claude, the `/task-create` skill will automatically create issues in Linear and prepend the ID to the group name.

### Data storage

All task data lives inside the project, in a directory ignored by git:

```
<project>/.claude/tasks/
├── tasks.json        # Active groups and tasks
├── archive.json      # Auto-archived completed groups
├── config.json       # Tracker settings (type, base URL)
└── docs/             # Markdown files with task details
    └── <groupId>/
        └── <taskId>.md
```

## Development

Run a sandboxed IDE instance with the plugin loaded (no install needed):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runIde
```

### Targeting a different IDE

By default, the plugin targets PyCharm (`PY`). To target IntelliJ IDEA, change in `gradle.properties`:

```properties
platformType = IC
```

Common values: `PY` (PyCharm), `IC` (IntelliJ Community), `IU` (IntelliJ Ultimate), `WS` (WebStorm).

## License

MIT
