#!/usr/bin/env bash

set -euo pipefail

TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$(cd "$TEST_DIR/.." && pwd)"
REGENERATION_SCRIPT="$MODULE_DIR/regenerate-jooq-schema.sh"
TRACKED_CONFIG="$MODULE_DIR/jooq-codegen.xml"
TEST_ROOT="$(mktemp -d)"
LIB_DIR="$TEST_ROOT/lib"
BIN_DIR="$TEST_ROOT/bin"
STDOUT_FILE="$TEST_ROOT/stdout.txt"
STDERR_FILE="$TEST_ROOT/stderr.txt"
CONFIG_SNAPSHOT="$TEST_ROOT/jooq-codegen.xml"
PODMAN_MARKER="$TEST_ROOT/podman-executed"
JAVA_MARKER="$TEST_ROOT/java-executed"

cleanup() {
    rm -r -- "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$LIB_DIR" "$BIN_DIR"
cp "$TRACKED_CONFIG" "$CONFIG_SNAPSHOT"

touch \
    "$LIB_DIR/jooq-meta-3.19.30.jar" \
    "$LIB_DIR/jooq-3.19.30.jar" \
    "$LIB_DIR/jakarta.xml.bind-api-4.0.4.jar" \
    "$LIB_DIR/reactive-streams.jar" \
    "$LIB_DIR/r2dbc-spi.jar" \
    "$LIB_DIR/postgresql-42.7.1.jar"

printf '#!/usr/bin/env bash\ntouch "%s"\nexit 97\n' "$PODMAN_MARKER" > "$BIN_DIR/podman"
printf '#!/usr/bin/env bash\ntouch "%s"\nexit 98\n' "$JAVA_MARKER" > "$BIN_DIR/java"
chmod +x "$BIN_DIR/podman" "$BIN_DIR/java"

set +e
JOOQ_GENERATOR_LIB_DIR="$LIB_DIR" \
PATH="$BIN_DIR:$PATH" \
bash "$REGENERATION_SCRIPT" >"$STDOUT_FILE" 2>"$STDERR_FILE"
exit_code=$?
set -e

missing_jar="$LIB_DIR/jooq-codegen-3.19.30.jar"
expected_stderr="FAIL: required jOOQ generator dependency missing: $missing_jar"
actual_stderr="$(<"$STDERR_FILE")"

if [[ "$exit_code" -eq 0 ]]; then
    echo "FAIL: missing generator dependency returned exit code 0" >&2
    exit 1
fi

if [[ "$actual_stderr" != "$expected_stderr" ]]; then
    echo "FAIL: missing generator dependency stderr mismatch" >&2
    printf 'Expected: %s\nActual: %s\n' "$expected_stderr" "$actual_stderr" >&2
    exit 1
fi

if [[ -e "$PODMAN_MARKER" ]]; then
    echo "FAIL: PostgreSQL command executed before dependency preflight" >&2
    exit 1
fi

if [[ -e "$JAVA_MARKER" ]]; then
    echo "FAIL: GenerationTool command executed with a missing dependency" >&2
    exit 1
fi

if ! cmp -s "$CONFIG_SNAPSHOT" "$TRACKED_CONFIG"; then
    echo "FAIL: tracked jOOQ config changed during dependency preflight" >&2
    exit 1
fi

if grep -Eq 'Schema regeneration complete|JOOQ_GENERATION_TOOL_EXECUTED=YES' "$STDOUT_FILE" "$STDERR_FILE"; then
    echo "FAIL: regeneration reported success with a missing dependency" >&2
    exit 1
fi

echo "MISSING_GENERATOR_JAR_NEGATIVE_CONTROL=PASS"
echo "MISSING_GENERATOR_JAR_EXIT_CODE=$exit_code"
echo "JOOQ_PREFLIGHT_BEFORE_POSTGRESQL=YES"
echo "JOOQ_CONFIG_UNCHANGED_ON_PREFLIGHT_FAILURE=YES"

NO_OP_ROOT="$TEST_ROOT/no-op-fixture"
NO_OP_MODULE="$NO_OP_ROOT/typed-schema-module"
NO_OP_LIB_DIR="$NO_OP_MODULE/lib"
NO_OP_BIN_DIR="$NO_OP_ROOT/bin"
NO_OP_STDOUT="$NO_OP_ROOT/stdout.txt"
NO_OP_STDERR="$NO_OP_ROOT/stderr.txt"
NO_OP_JAVA_MARKER="$NO_OP_ROOT/java-executed"
NO_OP_CONFIG_SNAPSHOT="$NO_OP_ROOT/jooq-codegen.xml"
NO_OP_GENERATED_DIR="$NO_OP_MODULE/src/main/java/com/example/platform/typedschema/jooq/generated"

mkdir -p \
    "$NO_OP_LIB_DIR" \
    "$NO_OP_BIN_DIR" \
    "$NO_OP_GENERATED_DIR" \
    "$NO_OP_ROOT/platform-app/src/main/resources/db/migration"
cp "$REGENERATION_SCRIPT" "$NO_OP_MODULE/regenerate-jooq-schema.sh"
cp "$TRACKED_CONFIG" "$NO_OP_MODULE/jooq-codegen.xml"
cp "$TRACKED_CONFIG" "$NO_OP_CONFIG_SNAPSHOT"
touch "$NO_OP_ROOT/platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
touch "$NO_OP_GENERATED_DIR/Stale.java"
touch \
    "$NO_OP_LIB_DIR/jooq-codegen-3.19.30.jar" \
    "$NO_OP_LIB_DIR/jooq-meta-3.19.30.jar" \
    "$NO_OP_LIB_DIR/jooq-3.19.30.jar" \
    "$NO_OP_LIB_DIR/jakarta.xml.bind-api-4.0.4.jar" \
    "$NO_OP_LIB_DIR/reactive-streams.jar" \
    "$NO_OP_LIB_DIR/r2dbc-spi.jar" \
    "$NO_OP_LIB_DIR/postgresql-42.7.1.jar"

printf '#!/usr/bin/env bash\nexit 0\n' > "$NO_OP_BIN_DIR/podman"
printf '#!/usr/bin/env bash\ntouch "%s"\nexit 0\n' "$NO_OP_JAVA_MARKER" > "$NO_OP_BIN_DIR/java"
chmod +x "$NO_OP_BIN_DIR/podman" "$NO_OP_BIN_DIR/java"

set +e
PATH="$NO_OP_BIN_DIR:$PATH" \
bash "$NO_OP_MODULE/regenerate-jooq-schema.sh" >"$NO_OP_STDOUT" 2>"$NO_OP_STDERR"
no_op_exit_code=$?
set -e

if [[ ! -e "$NO_OP_JAVA_MARKER" ]]; then
    echo "FAIL: no-op control did not reach the GenerationTool process" >&2
    exit 1
fi

if [[ "$no_op_exit_code" -eq 0 ]]; then
    echo "FAIL: no-op GenerationTool process returned regeneration success" >&2
    exit 1
fi

if grep -q 'JOOQ_GENERATION_TOOL_EXECUTED=YES' "$NO_OP_STDOUT" "$NO_OP_STDERR"; then
    echo "FAIL: no-op GenerationTool process emitted the execution-success marker" >&2
    exit 1
fi

expected_no_op_stderr="FAIL: GenerationTool produced no complete generated source tree"
actual_no_op_stderr="$(<"$NO_OP_STDERR")"
if [[ "$actual_no_op_stderr" != "$expected_no_op_stderr" ]]; then
    echo "FAIL: no-op GenerationTool stderr mismatch" >&2
    printf 'Expected: %s\nActual: %s\n' "$expected_no_op_stderr" "$actual_no_op_stderr" >&2
    exit 1
fi

if ! cmp -s "$NO_OP_CONFIG_SNAPSHOT" "$NO_OP_MODULE/jooq-codegen.xml"; then
    echo "FAIL: no-op GenerationTool process changed tracked jOOQ config" >&2
    exit 1
fi

echo "NO_OP_GENERATOR_NEGATIVE_CONTROL=PASS"
echo "NO_OP_GENERATOR_EXIT_CODE=$no_op_exit_code"
