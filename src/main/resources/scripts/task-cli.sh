#!/usr/bin/env bash
# task-cli.sh — CLI helper for managing tasks in .claude/tasks/tasks.json
# Used by Claude skills to avoid reading/writing raw JSON manually.
#
# Usage:
#   task-cli.sh list                           — list all groups and tasks (compact)
#   task-cli.sh list --group <groupId>         — list tasks in a specific group
#   task-cli.sh get <taskId|groupId>           — get full details of a task or group
#   task-cli.sh status <taskId> <status>       — update task status (new|in_progress|completed|paused|cancelled)
#   task-cli.sh commit <taskId> <commitHash>   — attach a commit hash to a task
#   task-cli.sh add-group <name>               — create a new group, prints groupId
#   task-cli.sh add-task <groupId> <name> [description] — create a new task, prints taskId
#   task-cli.sh config                         — show tracker config

set -euo pipefail

TASKS_DIR=".claude/tasks"
TASKS_FILE="$TASKS_DIR/tasks.json"
DOCS_DIR="$TASKS_DIR/docs"
CONFIG_FILE="$TASKS_DIR/config.json"

ensure_dirs() {
    mkdir -p "$TASKS_DIR" "$DOCS_DIR"
    if [ ! -f "$TASKS_FILE" ]; then
        echo '{"groups":[]}' > "$TASKS_FILE"
    fi
}

short_uuid() {
    uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' | cut -c1-8 || \
    python3 -c "import uuid; print(str(uuid.uuid4())[:8])"
}

now_iso() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

cmd_list() {
    ensure_dirs
    local group_filter=""
    if [ "${1:-}" = "--group" ] && [ -n "${2:-}" ]; then
        group_filter="$2"
    fi

    if [ -n "$group_filter" ]; then
        python3 -c "
import json, sys
with open('$TASKS_FILE') as f:
    data = json.load(f)
for g in data['groups']:
    if g['id'] == '$group_filter':
        print(f\"Group: {g['name']} [{g['id']}] ({len(g['tasks'])} tasks)\")
        for t in g['tasks']:
            commit = f\" commit:{t.get('commitId','')}\" if t.get('commitId') else ''
            print(f\"  [{t['status']:12s}] {t['name']} [{t['id']}]{commit}\")
        break
else:
    print('Group not found', file=sys.stderr); sys.exit(1)
"
    else
        python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
if not data['groups']:
    print('No groups found.')
for g in data['groups']:
    statuses = {}
    for t in g['tasks']:
        s = t['status']
        statuses[s] = statuses.get(s, 0) + 1
    status_str = ', '.join(f'{v} {k}' for k, v in statuses.items())
    print(f\"Group: {g['name']} [{g['id']}] ({len(g['tasks'])} tasks: {status_str})\")
    for t in g['tasks']:
        commit = f\" commit:{t.get('commitId','')}\" if t.get('commitId') else ''
        print(f\"  [{t['status']:12s}] {t['name']} [{t['id']}]{commit}\")
"
    fi
}

cmd_get() {
    ensure_dirs
    local target_id="$1"
    python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
for g in data['groups']:
    if g['id'] == '$target_id':
        print(json.dumps(g, indent=2))
        exit(0)
    for t in g['tasks']:
        if t['id'] == '$target_id':
            result = dict(t)
            result['groupId'] = g['id']
            result['groupName'] = g['name']
            print(json.dumps(result, indent=2))
            exit(0)
print('{\"error\": \"not found\"}')
exit(1)
"
}

cmd_status() {
    ensure_dirs
    local task_id="$1"
    local new_status="$2"
    local now
    now=$(now_iso)

    python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
found = False
for g in data['groups']:
    for i, t in enumerate(g['tasks']):
        if t['id'] == '$task_id':
            g['tasks'][i]['status'] = '$new_status'
            g['tasks'][i]['updatedAt'] = '$now'
            found = True
            break
    if found:
        break
if not found:
    print('Task not found'); exit(1)
with open('$TASKS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
print('OK: $task_id -> $new_status')
"
}

cmd_commit() {
    ensure_dirs
    local task_id="$1"
    local commit_hash="$2"
    local now
    now=$(now_iso)

    python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
found = False
for g in data['groups']:
    for i, t in enumerate(g['tasks']):
        if t['id'] == '$task_id':
            g['tasks'][i]['commitId'] = '$commit_hash'
            g['tasks'][i]['updatedAt'] = '$now'
            found = True
            break
    if found:
        break
if not found:
    print('Task not found'); exit(1)
with open('$TASKS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
print('OK: $task_id <- commit $commit_hash')
"
}

cmd_add_group() {
    ensure_dirs
    local name="$1"
    local group_id
    group_id=$(short_uuid)
    local now
    now=$(now_iso)

    python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
max_order = max((g['order'] for g in data['groups']), default=0)
data['groups'].append({
    'id': '$group_id',
    'name': $(python3 -c "import json; print(json.dumps('$name'))"),
    'order': max_order + 1,
    'createdAt': '$now',
    'tasks': []
})
with open('$TASKS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
print('$group_id')
"
}

cmd_add_task() {
    ensure_dirs
    local group_id="$1"
    local name="$2"
    local description="${3:-}"
    local task_id
    task_id=$(short_uuid)
    local now
    now=$(now_iso)
    local md_path="$group_id/$task_id.md"

    python3 -c "
import json
with open('$TASKS_FILE') as f:
    data = json.load(f)
found = False
for g in data['groups']:
    if g['id'] == '$group_id':
        g['tasks'].append({
            'id': '$task_id',
            'name': $(python3 -c "import json; print(json.dumps('$name'))"),
            'description': $(python3 -c "import json; print(json.dumps('$description'))"),
            'status': 'new',
            'mdFile': '$md_path',
            'createdAt': '$now',
            'updatedAt': '$now',
            'commitId': ''
        })
        found = True
        break
if not found:
    print('Group not found'); exit(1)
with open('$TASKS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
print('$task_id')
"

    # Create MD file
    mkdir -p "$DOCS_DIR/$group_id"
    cat > "$DOCS_DIR/$md_path" << MDEOF
# $name

## Description
${description:-Add description here.}

## Instructions
Add detailed instructions here.

## Acceptance Criteria
- [ ] Define acceptance criteria
MDEOF

    echo "MD: $DOCS_DIR/$md_path"
}

cmd_config() {
    if [ -f "$CONFIG_FILE" ]; then
        cat "$CONFIG_FILE"
    else
        echo '{"type":"NONE","baseUrl":""}'
    fi
}

# --- Main ---
case "${1:-help}" in
    list)     shift; cmd_list "$@" ;;
    get)      cmd_get "$2" ;;
    status)   cmd_status "$2" "$3" ;;
    commit)   cmd_commit "$2" "$3" ;;
    add-group) cmd_add_group "$2" ;;
    add-task)  cmd_add_task "$2" "$3" "${4:-}" ;;
    config)   cmd_config ;;
    help|*)
        echo "Usage: task-cli.sh <command> [args]"
        echo ""
        echo "Commands:"
        echo "  list [--group <id>]                  List groups and tasks"
        echo "  get <id>                             Get task or group details"
        echo "  status <taskId> <status>             Update task status"
        echo "  commit <taskId> <hash>               Attach commit hash to task"
        echo "  add-group <name>                     Create group, prints ID"
        echo "  add-task <groupId> <name> [desc]     Create task, prints ID"
        echo "  config                               Show tracker config"
        ;;
esac
