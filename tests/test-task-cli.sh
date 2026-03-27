#!/usr/bin/env bash
# Tests for task-cli.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CLI="$SCRIPT_DIR/src/main/resources/scripts/task-cli.sh"
TEST_DIR=$(mktemp -d)
TASKS_DIR="$TEST_DIR/.claude/tasks"
TASKS_FILE="$TASKS_DIR/tasks.json"
DOCS_DIR="$TASKS_DIR/docs"

PASSED=0
FAILED=0
TOTAL=0

cleanup() { rm -rf "$TEST_DIR"; }
trap cleanup EXIT

setup() {
    rm -rf "$TASKS_DIR"
    mkdir -p "$TASKS_DIR" "$DOCS_DIR"
    echo '{"groups":[]}' > "$TASKS_FILE"
}

assert_eq() {
    local desc="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$expected" = "$actual" ]; then
        echo "  ✓ $desc"
        PASSED=$((PASSED + 1))
    else
        echo "  ✗ $desc"
        echo "    expected: $expected"
        echo "    actual:   $actual"
        FAILED=$((FAILED + 1))
    fi
}

assert_match() {
    local desc="$1" pattern="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$actual" | grep -qE "$pattern"; then
        echo "  ✓ $desc"
        PASSED=$((PASSED + 1))
    else
        echo "  ✗ $desc"
        echo "    pattern:  $pattern"
        echo "    actual:   $actual"
        FAILED=$((FAILED + 1))
    fi
}

assert_not_empty() {
    local desc="$1" actual="$2"
    TOTAL=$((TOTAL + 1))
    if [ -n "$actual" ]; then
        echo "  ✓ $desc"
        PASSED=$((PASSED + 1))
    else
        echo "  ✗ $desc (empty)"
        FAILED=$((FAILED + 1))
    fi
}

# Run CLI in the test directory context, capture first line (ID output)
run_cli() {
    (cd "$TEST_DIR" && bash "$CLI" "$@" 2>&1)
}

# Run CLI and return only the first line (for commands that print ID + extra info)
run_cli_id() {
    local output
    output=$(cd "$TEST_DIR" && bash "$CLI" "$@" 2>&1)
    echo "$output" | head -1 | tr -d '[:space:]'
}

# Extract JSON field with python
json_field() {
    local file="$1" path="$2"
    python3 -c "
import json
with open('$file') as f:
    data = json.load(f)
val = data
for key in '$path'.split('.'):
    if key.isdigit():
        val = val[int(key)]
    else:
        val = val[key]
print(val)
"
}

ISO_PATTERN='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'

# ============================================
echo "=== add-group ==="
setup

GID=$(run_cli_id add-group "Test Group")
assert_match "returns 8-char ID" '^[a-f0-9]{8}$' "$GID"

GROUP_NAME=$(json_field "$TASKS_FILE" "groups.0.name")
assert_eq "group name stored" "Test Group" "$GROUP_NAME"

GROUP_CREATED=$(json_field "$TASKS_FILE" "groups.0.createdAt")
assert_match "createdAt is valid ISO timestamp" "$ISO_PATTERN" "$GROUP_CREATED"

# Verify createdAt is not in the future
NOW_EPOCH=$(date -u +%s)
CREATED_EPOCH=$(python3 -c "
from datetime import datetime, timezone
dt = datetime.strptime('$GROUP_CREATED', '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
print(int(dt.timestamp()))
")
DIFF=$((NOW_EPOCH - CREATED_EPOCH))
TOTAL=$((TOTAL + 1))
if [ "$DIFF" -ge -2 ] && [ "$DIFF" -le 5 ]; then
    echo "  ✓ createdAt is within 5 seconds of now (diff=${DIFF}s)"
    PASSED=$((PASSED + 1))
else
    echo "  ✗ createdAt is NOT close to now (diff=${DIFF}s, created=$GROUP_CREATED)"
    FAILED=$((FAILED + 1))
fi

GROUP_ORDER=$(json_field "$TASKS_FILE" "groups.0.order")
assert_eq "first group order is 1" "1" "$GROUP_ORDER"

# ============================================
echo ""
echo "=== add-group (second group) ==="

GID2=$(run_cli_id add-group "Second Group")
GROUP2_ORDER=$(json_field "$TASKS_FILE" "groups.1.order")
assert_eq "second group order is 2" "2" "$GROUP2_ORDER"

GROUP_COUNT=$(python3 -c "import json; print(len(json.load(open('$TASKS_FILE'))['groups']))")
assert_eq "two groups exist" "2" "$GROUP_COUNT"

# ============================================
echo ""
echo "=== add-task ==="
setup

GID=$(run_cli_id add-group "Task Group")
TID=$(run_cli_id add-task "$GID" "My Task" "A description with 'quotes' and (parens)")
assert_match "returns 8-char task ID" '^[a-f0-9]{8}$' "$TID"

TASK_NAME=$(json_field "$TASKS_FILE" "groups.0.tasks.0.name")
assert_eq "task name stored" "My Task" "$TASK_NAME"

TASK_DESC=$(json_field "$TASKS_FILE" "groups.0.tasks.0.description")
assert_eq "description with special chars" "A description with 'quotes' and (parens)" "$TASK_DESC"

TASK_STATUS=$(json_field "$TASKS_FILE" "groups.0.tasks.0.status")
assert_eq "initial status is new" "new" "$TASK_STATUS"

TASK_CREATED=$(json_field "$TASKS_FILE" "groups.0.tasks.0.createdAt")
assert_match "task createdAt is valid ISO" "$ISO_PATTERN" "$TASK_CREATED"

TASK_UPDATED=$(json_field "$TASKS_FILE" "groups.0.tasks.0.updatedAt")
assert_eq "createdAt equals updatedAt on creation" "$TASK_CREATED" "$TASK_UPDATED"

# Verify task createdAt is not in the future
TASK_EPOCH=$(python3 -c "
from datetime import datetime, timezone
dt = datetime.strptime('$TASK_CREATED', '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
print(int(dt.timestamp()))
")
TASK_DIFF=$((NOW_EPOCH - TASK_EPOCH))
TOTAL=$((TOTAL + 1))
if [ "$TASK_DIFF" -ge -5 ] && [ "$TASK_DIFF" -le 10 ]; then
    echo "  ✓ task createdAt is within 10 seconds of now (diff=${TASK_DIFF}s)"
    PASSED=$((PASSED + 1))
else
    echo "  ✗ task createdAt is NOT close to now (diff=${TASK_DIFF}s, created=$TASK_CREATED)"
    FAILED=$((FAILED + 1))
fi

# MD file created (script creates it relative to cwd)
MD_FILE="$TEST_DIR/.claude/tasks/docs/$GID/${TID}.md"
TOTAL=$((TOTAL + 1))
if [ -f "$MD_FILE" ]; then
    echo "  ✓ MD file created at docs/$GID/$TID.md"
    PASSED=$((PASSED + 1))
else
    # Debug: show what exists
    echo "  ✗ MD file not found at $MD_FILE"
    echo "    docs contents: $(find "$TEST_DIR/.claude/tasks/docs" -type f 2>/dev/null || echo 'empty')"
    FAILED=$((FAILED + 1))
fi

# ============================================
echo ""
echo "=== add-task (special characters in description) ==="
setup

GID=$(run_cli_id add-group "Special Chars")
TID=$(run_cli_id add-task "$GID" "Complex task" 'Current migrations (e.g. update_adk_to_1_14_1) import types from v0 schema (DynamicPickleType, DynamicJSON) and add columns that will "not" exist')
TASK_NAME2=$(json_field "$TASKS_FILE" "groups.0.tasks.0.name")
assert_eq "task with complex description created" "Complex task" "$TASK_NAME2"

# ============================================
echo ""
echo "=== status ==="
setup

GID=$(run_cli_id add-group "Status Group")
TID=$(run_cli_id add-task "$GID" "Status Task" "test")
sleep 1

run_cli status "$TID" in_progress > /dev/null
TASK_STATUS=$(json_field "$TASKS_FILE" "groups.0.tasks.0.status")
assert_eq "status updated to in_progress" "in_progress" "$TASK_STATUS"

NEW_UPDATED=$(json_field "$TASKS_FILE" "groups.0.tasks.0.updatedAt")
TASK_CREATED=$(json_field "$TASKS_FILE" "groups.0.tasks.0.createdAt")
TOTAL=$((TOTAL + 1))
if [ "$NEW_UPDATED" != "$TASK_CREATED" ]; then
    echo "  ✓ updatedAt changed after status update"
    PASSED=$((PASSED + 1))
else
    echo "  ✗ updatedAt did not change (still $TASK_CREATED)"
    FAILED=$((FAILED + 1))
fi

# Verify updatedAt is AFTER createdAt
TOTAL=$((TOTAL + 1))
if [[ "$NEW_UPDATED" > "$TASK_CREATED" ]]; then
    echo "  ✓ updatedAt ($NEW_UPDATED) is after createdAt ($TASK_CREATED)"
    PASSED=$((PASSED + 1))
else
    echo "  ✗ updatedAt ($NEW_UPDATED) is NOT after createdAt ($TASK_CREATED)"
    FAILED=$((FAILED + 1))
fi

# ============================================
echo ""
echo "=== commit ==="
setup

GID=$(run_cli_id add-group "Commit Group")
TID=$(run_cli_id add-task "$GID" "Commit Task" "test")
run_cli commit "$TID" "abc1234def5678" > /dev/null

COMMIT_ID=$(json_field "$TASKS_FILE" "groups.0.tasks.0.commitId")
assert_eq "commitId stored" "abc1234def5678" "$COMMIT_ID"

# ============================================
echo ""
echo "=== list ==="
setup

GID=$(run_cli_id add-group "List Group")
run_cli add-task "$GID" "Task One" "desc1" > /dev/null
run_cli add-task "$GID" "Task Two" "desc2" > /dev/null
run_cli status "$(json_field "$TASKS_FILE" "groups.0.tasks.0.id")" completed > /dev/null

LIST_OUT=$(run_cli list)
assert_match "list shows group" "List Group" "$LIST_OUT"
assert_match "list shows active task" "Task Two" "$LIST_OUT"

LIST_ALL=$(run_cli list --all)
assert_match "list --all shows completed task" "Task One" "$LIST_ALL"

# ============================================
echo ""
echo "=== get ==="
setup

GID=$(run_cli_id add-group "Get Group")
TID=$(run_cli_id add-task "$GID" "Get Task" "test desc")

GET_OUT=$(run_cli get "$TID")
assert_match "get returns task JSON" '"name": "Get Task"' "$GET_OUT"

GET_GROUP=$(run_cli get "$GID")
assert_match "get returns group JSON" '"name": "Get Group"' "$GET_GROUP"

# ============================================
echo ""
echo "=== config (no config file) ==="
setup

CONFIG_OUT=$(run_cli config)
assert_match "config shows NONE when no tracker configured" '"type".*NONE|No tracker' "$CONFIG_OUT"

# ============================================
echo ""
echo "================================"
echo "Results: $PASSED/$TOTAL passed, $FAILED failed"
if [ "$FAILED" -gt 0 ]; then
    echo "SOME TESTS FAILED"
    exit 1
else
    echo "ALL TESTS PASSED ✓"
fi
