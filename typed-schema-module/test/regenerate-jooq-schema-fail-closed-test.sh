#!/usr/bin/env bash
# Fail-closed guard for the formal jOOQ regeneration entry.
#
# All cases run in temporary fixtures outside the repository and intercept
# `podman`, host `java`, and `git` with fake commands. No real database,
# PostgreSQL container, Flyway, GenerationTool, or Git index operation occurs.
#
# Covered contracts:
#   - missing generator dependency
#   - missing explicit isolated trust opt-in
#   - missing Flyway dependency
#   - isolated Pod initialization failure
#   - runtime GenerationTool failure after preconditions
#   - staging inside the repository
#   - non-empty staging directory
#   - generated tree and Git index non-modification

set -euo pipefail

TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$(cd "$TEST_DIR/.." && pwd)"
REGENERATION_SCRIPT="$MODULE_DIR/regenerate-jooq-schema.sh"
TRACKED_CONFIG="$MODULE_DIR/jooq-codegen.xml"
TEST_ROOT="$(mktemp -d)"
CASE_ROOT=""
CASE_ID=""
STDOUT_FILE=""
STDERR_FILE=""
PODMAN_LOG=""
JAVA_MARKER=""
GIT_MARKER=""
CONFIG_SNAPSHOT=""
GENERATED_SENTINEL=""
GENERATED_HASH=""
INDEX_SENTINEL=""
INDEX_HASH=""

cleanup() {
    rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

hash_file() {
    sha256sum "$1" | awk '{print $1}'
}

write_fake_commands() {
    local bin_dir="$CASE_ROOT/bin"
    mkdir -p "$bin_dir"

    cat > "$bin_dir/podman" <<'FAKE_PODMAN'
#!/usr/bin/env bash
set -u
if [[ -n "${FAKE_PODMAN_LOG:-}" ]]; then
    printf '%s\n' "$*" >> "$FAKE_PODMAN_LOG"
fi
case "${1:-}" in
    image)
        exit 0
        ;;
    pod)
        case "${2:-}" in
            create)
                if [[ "${FAKE_PODMAN_FAIL_ON:-}" == "pod-create" ]]; then
                    exit 9
                fi
                exit 0
                ;;
            start)
                exit 0
                ;;
            inspect)
                echo "fake-infra"
                exit 0
                ;;
            rm)
                exit 0
                ;;
        esac
        ;;
    run)
        case "$*" in
            *GenerationTool*)
                if [[ "${FAKE_PODMAN_FAIL_ON:-}" == "generation" ]]; then
                    exit 7
                fi
                exit 0
                ;;
            *javac*|*FlywayRunner*)
                exit 0
                ;;
        esac
        exit 0
        ;;
    exec)
        case "$*" in
            *"server_version_num"*)
                echo "18"
                ;;
            *"show listen_addresses"*)
                echo "127.0.0.1"
                ;;
            *"where success"*)
                echo "1"
                ;;
            *"select count(*) from flyway_schema_history"*)
                echo "1"
                ;;
            *"pg_isready"*)
                exit 0
                ;;
            *"00000000"*)
                exit 1
                ;;
        esac
        exit 0
        ;;
    port)
        exit 0
        ;;
    inspect)
        echo "none"
        exit 0
        ;;
    stop|rm)
        exit 0
        ;;
esac
exit 0
FAKE_PODMAN
    chmod +x "$bin_dir/podman"

    cat > "$bin_dir/java" <<FAKE_JAVA
#!/usr/bin/env bash
touch "$JAVA_MARKER"
exit 0
FAKE_JAVA
    chmod +x "$bin_dir/java"

    cat > "$bin_dir/git" <<FAKE_GIT
#!/usr/bin/env bash
touch "$GIT_MARKER"
exit 0
FAKE_GIT
    chmod +x "$bin_dir/git"
}

prepare_case() {
    CASE_ID="$1"
    CASE_ROOT="$TEST_ROOT/$CASE_ID"
    STDOUT_FILE="$CASE_ROOT/stdout.txt"
    STDERR_FILE="$CASE_ROOT/stderr.txt"
    PODMAN_LOG="$CASE_ROOT/podman.log"
    JAVA_MARKER="$CASE_ROOT/java-executed"
    GIT_MARKER="$CASE_ROOT/git-executed"

    mkdir -p \
        "$CASE_ROOT/typed-schema-module" \
        "$CASE_ROOT/platform-app/src/main/resources/db/migration" \
        "$CASE_ROOT/lib" \
        "$CASE_ROOT/flyway" \
        "$CASE_ROOT/typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated" \
        "$CASE_ROOT/.git"

    cp "$REGENERATION_SCRIPT" "$CASE_ROOT/typed-schema-module/regenerate-jooq-schema.sh"
    cp "$TRACKED_CONFIG" "$CASE_ROOT/typed-schema-module/jooq-codegen.xml"
    chmod +x "$CASE_ROOT/typed-schema-module/regenerate-jooq-schema.sh"
    touch "$CASE_ROOT/platform-app/src/main/resources/db/migration/V1__baseline.sql"
    printf '%s\n' "sentinel" > "$CASE_ROOT/typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/Sentinel.java"
    printf '%s\n' "index-sentinel" > "$CASE_ROOT/.git/index"

    for jar in \
        jooq-codegen-3.21.9.jar \
        jooq-meta-3.21.9.jar \
        jooq-3.21.9.jar \
        jakarta.xml.bind-api-4.0.4.jar \
        jakarta.activation-api-2.1.4.jar \
        r2dbc-spi-1.0.0.RELEASE.jar \
        reactive-streams-1.0.3.jar \
        slf4j-api-2.0.9.jar \
        postgresql-42.7.14.jar \
        checker-qual-3.41.0.jar; do
        : > "$CASE_ROOT/lib/$jar"
    done
    : > "$CASE_ROOT/flyway/flyway-core-11.20.3.jar"
    : > "$CASE_ROOT/flyway/flyway-database-postgresql-11.20.3.jar"

    write_fake_commands

    CONFIG_SNAPSHOT="$CASE_ROOT/config.before"
    cp "$TRACKED_CONFIG" "$CONFIG_SNAPSHOT"
    GENERATED_SENTINEL="$CASE_ROOT/typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/Sentinel.java"
    GENERATED_HASH="$(hash_file "$GENERATED_SENTINEL")"
    INDEX_SENTINEL="$CASE_ROOT/.git/index"
    INDEX_HASH="$(hash_file "$INDEX_SENTINEL")"
}

run_script() {
    local allow_trust="$1"
    local flyway_dir="$2"
    local fail_on="$3"
    local staging="$4"

    set +e
    (
        cd "$CASE_ROOT"
        env \
            PATH="$CASE_ROOT/bin:$PATH" \
            JOOQ_GENERATOR_LIB_DIR="$CASE_ROOT/lib" \
            JOOQ_STAGING_DIR="$staging" \
            JOOQ_FLYWAY_LIB_DIR="$flyway_dir" \
            JOOQ_PG_IMAGE="postgres:18-test-image" \
            JOOQ_ALLOW_TRUST="$allow_trust" \
            FAKE_PODMAN_LOG="$PODMAN_LOG" \
            FAKE_PODMAN_FAIL_ON="$fail_on" \
            bash "$CASE_ROOT/typed-schema-module/regenerate-jooq-schema.sh"
    ) > "$STDOUT_FILE" 2> "$STDERR_FILE"
    RUN_EXIT=$?
    set -e
}

assert_case_common() {
    local rc="$1"

    [[ "$rc" -ne 0 ]] || fail "$CASE_ID returned exit 0"
    [[ "$(hash_file "$CONFIG_SNAPSHOT")" == "$(hash_file "$CASE_ROOT/typed-schema-module/jooq-codegen.xml")" ]] || fail "$CASE_ID modified tracked config"
    [[ "$(hash_file "$GENERATED_SENTINEL")" == "$GENERATED_HASH" ]] || fail "$CASE_ID modified generated sentinel"
    [[ "$(hash_file "$INDEX_SENTINEL")" == "$INDEX_HASH" ]] || fail "$CASE_ID modified index sentinel"
    [[ ! -e "$JAVA_MARKER" ]] || fail "$CASE_ID invoked host java"
    [[ ! -e "$GIT_MARKER" ]] || fail "$CASE_ID invoked git"
    ! grep -q 'JOOQ_GENERATION_TOOL_EXECUTED=YES' "$STDOUT_FILE" "$STDERR_FILE" || fail "$CASE_ID reported generation success"
}

print_result() {
    echo "CASE=$CASE_ID RESULT=PASS EXIT=$RUN_EXIT"
}

# ---------------------------------------------------------------------------
# Case 1: missing generator dependency (preserves the original negative control)
# ---------------------------------------------------------------------------
prepare_case "missing-generator"
rm -f "$CASE_ROOT/lib/jooq-codegen-3.21.9.jar"
run_script "1" "$CASE_ROOT/flyway" "" "$TEST_ROOT/stage-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -Fq "FAIL: required jOOQ generator dependency missing:" "$STDERR_FILE" || fail "missing-generator error mismatch"
[[ ! -s "$PODMAN_LOG" ]] || fail "missing-generator reached podman"
print_result

# ---------------------------------------------------------------------------
# Case 2: missing explicit trust authorization
# ---------------------------------------------------------------------------
prepare_case "missing-trust"
run_script "" "$CASE_ROOT/flyway" "" "$TEST_ROOT/stage-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "JOOQ_ALLOW_TRUST=1" "$STDERR_FILE" || fail "missing-trust error mismatch"
[[ ! -s "$PODMAN_LOG" ]] || fail "missing-trust reached podman"
print_result

# ---------------------------------------------------------------------------
# Case 3: missing Flyway dependency
# ---------------------------------------------------------------------------
prepare_case "missing-flyway"
run_script "1" "" "" "$CASE_ROOT/stage"
assert_case_common "$RUN_EXIT"
grep -q "JOOQ_FLYWAY_LIB_DIR" "$STDERR_FILE" || fail "missing-flyway error mismatch"
[[ ! -s "$PODMAN_LOG" ]] || fail "missing-flyway reached podman"
print_result

# ---------------------------------------------------------------------------
# Case 4: isolated Pod initialization failure
# ---------------------------------------------------------------------------
prepare_case "pod-create-failure"
run_script "1" "$CASE_ROOT/flyway" "pod-create" "$TEST_ROOT/stage-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "pod create" "$PODMAN_LOG" || { echo "pod-create stderr:" >&2; cat "$STDERR_FILE" >&2; echo "podman log:" >&2; cat "$PODMAN_LOG" >&2; fail "pod-create failure did not reach pod creation"; }
! grep -q "GenerationTool" "$PODMAN_LOG" || fail "pod-create failure reached GenerationTool"
print_result

# ---------------------------------------------------------------------------
# Case 5: runtime GenerationTool failure after preconditions
# ---------------------------------------------------------------------------
prepare_case "generation-failure"
run_script "1" "$CASE_ROOT/flyway" "generation" "$TEST_ROOT/stage-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "GenerationTool" "$PODMAN_LOG" || fail "generation failure did not reach GenerationTool"
[[ ! -e "$TEST_ROOT/stage-$CASE_ID/manifest.sha256" ]] || fail "generation failure produced a manifest"
[[ ! -e "$TEST_ROOT/stage-$CASE_ID/summary.txt" ]] || fail "generation failure produced a summary"
grep -q "pod rm" "$PODMAN_LOG" || fail "generation failure did not clean the task Pod"
print_result

# ---------------------------------------------------------------------------
# Case 6: staging path inside the repository
# ---------------------------------------------------------------------------
prepare_case "staging-inside-repo"
run_script "1" "$CASE_ROOT/flyway" "" "$CASE_ROOT/typed-schema-module/forbidden-stage"
assert_case_common "$RUN_EXIT"
grep -q "outside the repository" "$STDERR_FILE" || fail "staging-inside-repo error mismatch"
! grep -q "pod create" "$PODMAN_LOG" || fail "staging-inside-repo reached Pod creation"
print_result

# ---------------------------------------------------------------------------
# Case 7: non-empty external staging directory
# ---------------------------------------------------------------------------
prepare_case "nonempty-staging"
mkdir -p "$TEST_ROOT/stage-$CASE_ID"
printf '%s\n' "do-not-overwrite" > "$TEST_ROOT/stage-$CASE_ID/existing.txt"
run_script "1" "$CASE_ROOT/flyway" "" "$TEST_ROOT/stage-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "absent or empty" "$STDERR_FILE" || { echo "nonempty stderr:" >&2; cat "$STDERR_FILE" >&2; fail "nonempty-staging error mismatch"; }
! grep -q "pod create" "$PODMAN_LOG" || fail "nonempty-staging reached Pod creation"
[[ "$(cat "$TEST_ROOT/stage-$CASE_ID/existing.txt")" == "do-not-overwrite" ]] || fail "nonempty-staging was modified"
print_result


assert_no_pod_create() {
    ! grep -q "pod create" "$PODMAN_LOG" || fail "$CASE_ID reached Pod creation"
}

# ---------------------------------------------------------------------------
# Case 8: empty custom staging
# ---------------------------------------------------------------------------
prepare_case "empty-staging"
run_script "1" "$CASE_ROOT/flyway" "" ""
assert_case_common "$RUN_EXIT"
grep -q "must not be empty" "$STDERR_FILE" || fail "empty-staging error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 9: relative staging
# ---------------------------------------------------------------------------
prepare_case "relative-staging"
run_script "1" "$CASE_ROOT/flyway" "" "relative-stage"
assert_case_common "$RUN_EXIT"
grep -q "absolute path" "$STDERR_FILE" || fail "relative-staging error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 10: dot-dot path
# ---------------------------------------------------------------------------
prepare_case "dotdot-staging"
run_script "1" "$CASE_ROOT/flyway" "" "$CASE_ROOT/../$CASE_ID-dotdot"
assert_case_common "$RUN_EXIT"
grep -q "must not contain" "$STDERR_FILE" || fail "dotdot-staging error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 11: final component symlink
# ---------------------------------------------------------------------------
prepare_case "symlink-final-component"
mkdir -p "$CASE_ROOT/symlink-target"
ln -s "$CASE_ROOT/symlink-target" "$TEST_ROOT/symlink-final-$CASE_ID"
run_script "1" "$CASE_ROOT/flyway" "" "$TEST_ROOT/symlink-final-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "symbolic link" "$STDERR_FILE" || fail "symlink-final error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 12: symlink parent component
# ---------------------------------------------------------------------------
prepare_case "symlink-parent-component"
ln -s "$CASE_ROOT" "$TEST_ROOT/symlink-parent-$CASE_ID"
run_script "1" "$CASE_ROOT/flyway" "" "$TEST_ROOT/symlink-parent-$CASE_ID/stage"
assert_case_common "$RUN_EXIT"
grep -q "symbolic link" "$STDERR_FILE" || fail "symlink-parent error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 13: symlink pointing into repository
# ---------------------------------------------------------------------------
prepare_case "symlink-into-repo"
ln -s "$CASE_ROOT/typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated" "$TEST_ROOT/symlink-repo-$CASE_ID"
run_script "1" "$CASE_ROOT/flyway" "" "$TEST_ROOT/symlink-repo-$CASE_ID"
assert_case_common "$RUN_EXIT"
grep -q "symbolic link" "$STDERR_FILE" || fail "symlink-into-repo error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 14: second staging is a symlink
# ---------------------------------------------------------------------------
prepare_case "second-staging-symlink"
first_stage="$TEST_ROOT/second-symlink-$CASE_ID"
ln -s "$CASE_ROOT" "${first_stage}-second"
run_script "1" "$CASE_ROOT/flyway" "" "$first_stage"
assert_case_common "$RUN_EXIT"
grep -q "symbolic link" "$STDERR_FILE" || fail "second-staging-symlink error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 15: second staging is non-empty
# ---------------------------------------------------------------------------
prepare_case "second-staging-nonempty"
first_stage="$TEST_ROOT/second-nonempty-$CASE_ID"
mkdir -p "${first_stage}-second"
printf '%s\n' "keep" > "${first_stage}-second/existing.txt"
run_script "1" "$CASE_ROOT/flyway" "" "$first_stage"
assert_case_common "$RUN_EXIT"
grep -q "absent or empty" "$STDERR_FILE" || fail "second-staging-nonempty error mismatch"
assert_no_pod_create
[[ "$(cat "${first_stage}-second/existing.txt")" == "keep" ]] || fail "second-staging-nonempty modified fixture"
print_result

# ---------------------------------------------------------------------------
# Case 16: derived second staging resolves inside the repository
# The fixture root ends in "-second", so the first staging is its parent
# without that suffix and the derived second staging is the repository root.
# ---------------------------------------------------------------------------
prepare_case "second-staging-inside-repo-second"
first_stage="$TEST_ROOT/second-staging-inside-repo"
run_script "1" "$CASE_ROOT/flyway" "" "$first_stage"
assert_case_common "$RUN_EXIT"
grep -q "outside the repository" "$STDERR_FILE" || fail "second-staging-inside-repo error mismatch"
assert_no_pod_create
print_result

# ---------------------------------------------------------------------------
# Case 16: valid external empty staging reaches the Pod boundary
# ---------------------------------------------------------------------------
prepare_case "valid-external-empty-staging"
valid_stage="$TEST_ROOT/valid-empty-$CASE_ID"
run_script "1" "$CASE_ROOT/flyway" "pod-create" "$valid_stage"
assert_case_common "$RUN_EXIT"
grep -q "pod create" "$PODMAN_LOG" || fail "valid staging did not reach Pod creation"
print_result

echo "FAIL_CLOSED_GUARD_CASES=PASS"
echo "FAIL_CLOSED_GUARD_REAL_PODMAN=NONE"
echo "FAIL_CLOSED_GUARD_REAL_JAVA=NONE"
echo "FAIL_CLOSED_GUARD_REAL_GIT=NONE"
