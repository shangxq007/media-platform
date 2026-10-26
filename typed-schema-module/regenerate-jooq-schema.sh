#!/usr/bin/env bash
# Formal jOOQ regeneration entry.
#
# Flyway 11.20.3 and jOOQ 3.21.9 run against a task-owned, rootless Podman Pod
# whose network is `none`. PostgreSQL and the Java execution container share the
# Pod network namespace and communicate only over that namespace's loopback.
# No host database port is published. Generated sources are written to an
# external staging directory and are never installed into the repository.
#
# Isolated trust is permitted only when JOOQ_ALLOW_TRUST=1 and the verified
# isolation preconditions hold. The script fails closed; it never falls back to
# bridge, host networking, or a host database.
#
# Required environment:
#   JOOQ_FLYWAY_LIB_DIR       Flyway 11.20.3 dependency directory
#   JOOQ_ALLOW_TRUST=1        explicit isolated trust opt-in
# Optional environment:
#   JOOQ_GENERATOR_LIB_DIR    directory with the 10 jOOQ generator jars
#   JOOQ_STAGING_DIR          external staging directory (must not be in repo)
#   JOOQ_EXPECTED_MANIFEST    expected C manifest for byte comparison
#   JOOQ_VERIFY_REPRODUCIBILITY=0 to skip the second generation
#   JOOQ_PG_IMAGE             PostgreSQL image ID (PG18 image required; no default)
#   JOOQ_JAVA_IMAGE           Java execution image
#
# Usage:
#   JOOQ_ALLOW_TRUST=1 JOOQ_FLYWAY_LIB_DIR=/path/to/flyway-libs \
#     ./typed-schema-module/regenerate-jooq-schema.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
if ! command -v realpath >/dev/null 2>&1; then
    echo "FAIL: realpath is required for safe staging validation" >&2
    exit 1
fi
ROOT_REAL="$(realpath -m -- "$ROOT_DIR")" || {
    echo "FAIL: cannot resolve repository root" >&2
    exit 1
}

SCHEMA_FILE="$ROOT_DIR/platform-app/src/main/resources/db/migration/V1__baseline.sql"
MIGRATION_DIR="$ROOT_DIR/platform-app/src/main/resources/db/migration"
CODEGEN_CONFIG="$SCRIPT_DIR/jooq-codegen.xml"

canonicalize_staging() {
    local input="$1"
    local resolved
    local prefix=""
    local part
    local -a parts

    if [[ -z "$input" ]]; then
        echo "FAIL: staging directory must not be empty" >&2
        return 1
    fi
    if [[ "$input" != /* ]]; then
        echo "FAIL: staging directory must be an absolute path" >&2
        return 1
    fi
    if [[ "$input" == *$'\n'* ]]; then
        echo "FAIL: staging directory contains a newline" >&2
        return 1
    fi
    if [[ "$input" == *"/../"* || "$input" == */.. ]]; then
        echo "FAIL: staging directory must not contain '..'" >&2
        return 1
    fi

    IFS='/' read -r -a parts <<< "${input#/}"
    for part in "${parts[@]}"; do
        [[ -z "$part" ]] && continue
        prefix="$prefix/$part"
        if [[ -L "$prefix" ]]; then
            echo "FAIL: staging path contains a symbolic link component: $prefix" >&2
            return 1
        fi
        [[ -e "$prefix" ]] || break
    done

    resolved="$(realpath -m -- "$input")" || {
        echo "FAIL: cannot resolve staging directory: $input" >&2
        return 1
    }
    if [[ "$resolved" == "/" ]]; then
        echo "FAIL: staging directory must not be the root directory" >&2
        return 1
    fi
    case "$resolved" in
        "$ROOT_REAL"|"$ROOT_REAL"/*)
            echo "FAIL: staging directory must be outside the repository: $resolved" >&2
            return 1
            ;;
    esac
    [[ "$resolved" != *$'\n'* ]] || {
        echo "FAIL: resolved staging path contains a newline" >&2
        return 1
    }
    printf '%s\n' "$resolved"
}

ensure_staging_dir() {
    local input="$1"
    local resolved

    resolved="$(canonicalize_staging "$input")" || return 1
    if [[ -e "$resolved" ]]; then
        if [[ -L "$resolved" ]]; then
            echo "FAIL: staging directory must not be a symbolic link: $resolved" >&2
            return 1
        fi
        if [[ ! -d "$resolved" ]]; then
            echo "FAIL: staging path is not a directory: $resolved" >&2
            return 1
        fi
        if [[ -n "$(find "$resolved" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
            echo "FAIL: staging directory must be absent or empty: $resolved" >&2
            return 1
        fi
    else
        mkdir -p -- "$resolved" || {
            echo "FAIL: cannot create staging directory: $resolved" >&2
            return 1
        }
        resolved="$(canonicalize_staging "$input")" || return 1
        if [[ -L "$resolved" || ! -d "$resolved" ]]; then
            echo "FAIL: staging path changed during creation: $resolved" >&2
            return 1
        fi
        if [[ -n "$(find "$resolved" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
            echo "FAIL: staging directory was not empty after creation: $resolved" >&2
            return 1
        fi
    fi
    printf '%s\n' "$resolved"
}

JOOQ_VERSION="3.21.9"
DB_NAME="${JOOQ_DB_NAME:-jooq_codegen}"
DB_USER="${JOOQ_DB_USER:-codegen}"
DB_AUTH_PLACEHOLDER="__JOOQ_DB_AUTH__"
DB_USER_PLACEHOLDER="__JOOQ_DB_USER__"
DB_URL_PLACEHOLDER="__JOOQ_JDBC_URL__"

JAVA_IMAGE="${JOOQ_JAVA_IMAGE:-docker.io/library/gradle:9.1-jdk25-noble}"
PG_IMAGE="${JOOQ_PG_IMAGE:?JOOQ_PG_IMAGE must identify an approved PostgreSQL 18 image}"
GENERATOR_LIB_DIR="${JOOQ_GENERATOR_LIB_DIR:-$SCRIPT_DIR/lib}"
FLYWAY_LIB_DIR="${JOOQ_FLYWAY_LIB_DIR:-}"

POD_NAME="jooq-generation-pod-$(date +%s)-$$"
PG_CONTAINER="jooq-generation-pg18-$(date +%s)-$$"
RUNTIME_DIR=""
POD_CREATED=0
PG_STARTED=0
STAGING_DIR=""
STAGING_SECOND=""

required_jars=(
    "$GENERATOR_LIB_DIR/jooq-codegen-$JOOQ_VERSION.jar"
    "$GENERATOR_LIB_DIR/jooq-meta-$JOOQ_VERSION.jar"
    "$GENERATOR_LIB_DIR/jooq-$JOOQ_VERSION.jar"
    "$GENERATOR_LIB_DIR/jakarta.xml.bind-api-4.0.4.jar"
    "$GENERATOR_LIB_DIR/jakarta.activation-api-2.1.4.jar"
    "$GENERATOR_LIB_DIR/r2dbc-spi-1.0.0.RELEASE.jar"
    "$GENERATOR_LIB_DIR/reactive-streams-1.0.3.jar"
    "$GENERATOR_LIB_DIR/slf4j-api-2.0.9.jar"
    "$GENERATOR_LIB_DIR/postgresql-42.7.14.jar"
    "$GENERATOR_LIB_DIR/checker-qual-3.41.0.jar"
)

missing_dependency=0
for jar in "${required_jars[@]}"; do
    if [[ ! -f "$jar" ]]; then
        echo "FAIL: required jOOQ generator dependency missing: $jar" >&2
        missing_dependency=1
    fi
done
if [[ "$missing_dependency" -ne 0 ]]; then
    exit 1
fi

if [[ -z "$FLYWAY_LIB_DIR" || ! -d "$FLYWAY_LIB_DIR" ]]; then
    echo "FAIL: JOOQ_FLYWAY_LIB_DIR must point to the Flyway 11.20.3 dependency directory" >&2
    exit 1
fi
for jar in flyway-core-11.20.3.jar flyway-database-postgresql-11.20.3.jar; do
    if [[ ! -f "$FLYWAY_LIB_DIR/$jar" ]]; then
        echo "FAIL: required Flyway dependency missing: $FLYWAY_LIB_DIR/$jar" >&2
        exit 1
    fi
done

if [[ "${JOOQ_ALLOW_TRUST:-0}" != "1" ]]; then
    echo "FAIL: isolated database access requires explicit JOOQ_ALLOW_TRUST=1" >&2
    exit 1
fi

if [[ ! -f "$SCHEMA_FILE" ]]; then
    echo "FAIL: canonical schema file missing: $SCHEMA_FILE" >&2
    exit 1
fi

mapfile -t migrations < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' | sort -V)
if [[ "${#migrations[@]}" -ne 1 || "${migrations[0]}" != "V1__baseline.sql" ]]; then
    echo "FAIL: active migration inventory must contain exactly V1__baseline.sql" >&2
    printf '  found: %s\n' "${migrations[@]}" >&2
    exit 1
fi

if [[ ! -f "$CODEGEN_CONFIG" ]]; then
    echo "FAIL: tracked jOOQ generator config missing: $CODEGEN_CONFIG" >&2
    exit 1
fi
if [[ "$(grep -Fc '<excludes>flyway_schema_history</excludes>' "$CODEGEN_CONFIG" || true)" -ne 1 ]]; then
    echo "FAIL: generator config must exclude exactly flyway_schema_history" >&2
    exit 1
fi
if [[ "$(grep -Fc '<directory>src/main/java</directory>' "$CODEGEN_CONFIG" || true)" -ne 1 ]]; then
    echo "FAIL: generator config must retain exactly one relative target directory" >&2
    exit 1
fi
for marker in "$DB_URL_PLACEHOLDER" "$DB_USER_PLACEHOLDER" "$DB_AUTH_PLACEHOLDER"; do
    if [[ "$(grep -Fc "$marker" "$CODEGEN_CONFIG" || true)" -ne 1 ]]; then
        echo "FAIL: generator config must contain exactly one $marker placeholder" >&2
        exit 1
    fi
done

if ! command -v podman >/dev/null 2>&1; then
    echo "FAIL: podman is required" >&2
    exit 1
fi
if ! podman image exists "$PG_IMAGE" >/dev/null 2>&1; then
    echo "FAIL: frozen PostgreSQL image not present: $PG_IMAGE" >&2
    exit 1
fi
if ! podman image exists "$JAVA_IMAGE" >/dev/null 2>&1; then
    echo "FAIL: Java execution image not present: $JAVA_IMAGE" >&2
    exit 1
fi

if [[ -z "${JOOQ_STAGING_DIR+x}" ]]; then
    STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jooq-generation-staging.XXXXXX")" || {
        echo "FAIL: cannot create default staging directory" >&2
        exit 1
    }
else
    if [[ -z "$JOOQ_STAGING_DIR" ]]; then
        echo "FAIL: custom staging directory must not be empty" >&2
        exit 1
    fi
    STAGING_DIR="$JOOQ_STAGING_DIR"
fi

STAGING_DIR="$(ensure_staging_dir "$STAGING_DIR")" || exit 1
STAGING_SECOND="$(ensure_staging_dir "${STAGING_DIR%/}-second")" || exit 1

cleanup() {
    local rc=$?
    if [[ "$PG_STARTED" -eq 1 ]]; then
        podman stop "$PG_CONTAINER" >/dev/null 2>&1 || true
        podman rm "$PG_CONTAINER" >/dev/null 2>&1 || true
    fi
    if [[ "$POD_CREATED" -eq 1 ]]; then
        podman pod rm -f "$POD_NAME" >/dev/null 2>&1 || true
    fi
    if [[ -n "$RUNTIME_DIR" && -d "$RUNTIME_DIR" ]]; then
        rm -rf -- "$RUNTIME_DIR"
    fi
    exit "$rc"
}
trap cleanup EXIT

RUNTIME_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jooq-generation-runtime.XXXXXX")"
mkdir -p "$RUNTIME_DIR/classes"
JDBC_URL="jdbc:postgresql://127.0.0.1:5432/$DB_NAME"

echo "Creating isolated network=none Pod: $POD_NAME"
podman pod create --name "$POD_NAME" --network none \
    --infra-image "$JAVA_IMAGE" --infra-command "sleep infinity" >/dev/null
POD_CREATED=1
podman pod start "$POD_NAME" >/dev/null

if [[ -n "$(podman port "$POD_NAME" 2>/dev/null)" ]]; then
    echo "FAIL: Pod must not publish host ports" >&2
    exit 1
fi
INFRA_ID="$(podman pod inspect "$POD_NAME" --format '{{.InfraContainerID}}')"
if [[ "$(podman inspect "$INFRA_ID" --format '{{.HostConfig.NetworkMode}}')" != "none" ]]; then
    echo "FAIL: Pod infra network is not none" >&2
    exit 1
fi

echo "Starting PostgreSQL 18 in isolated Pod"
podman run -d --pod "$POD_NAME" --name "$PG_CONTAINER" --pull=never \
    -e "POSTGRES_DB=$DB_NAME" \
    -e "POSTGRES_USER=$DB_USER" \
    -e "POSTGRES_HOST_AUTH_METHOD=trust" \
    "$PG_IMAGE" -c listen_addresses=127.0.0.1 >/dev/null
PG_STARTED=1

if [[ -n "$(podman port "$PG_CONTAINER" 2>/dev/null)" ]]; then
    echo "FAIL: PostgreSQL container must not publish host ports" >&2
    exit 1
fi

for _ in $(seq 1 60); do
    if podman exec "$PG_CONTAINER" pg_isready -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done
if ! podman exec "$PG_CONTAINER" pg_isready -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    echo "FAIL: PostgreSQL did not become ready" >&2
    exit 1
fi
LISTEN_ADDR="$(podman exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "show listen_addresses")"
if [[ "$LISTEN_ADDR" != "127.0.0.1" ]]; then
    echo "FAIL: PostgreSQL listen_addresses is not 127.0.0.1: $LISTEN_ADDR" >&2
    exit 1
fi
if podman exec "$PG_CONTAINER" sh -c "grep -q '00000000' /proc/net/route"; then
    echo "FAIL: isolated Pod has a default route" >&2
    exit 1
fi
ACTUAL_MAJOR="$(podman exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select current_setting('server_version_num')::int/10000")"
if [[ "$ACTUAL_MAJOR" != "18" ]]; then
    echo "FAIL: PostgreSQL server major must be 18, observed: $ACTUAL_MAJOR" >&2
    exit 1
fi

cat > "$RUNTIME_DIR/FlywayRunner.java" <<'JAVA'
import org.flywaydb.core.Flyway;

public class FlywayRunner {
    public static void main(String[] args) {
        String url = args[0];
        String user = args[1];
        String migrationDir = args[2];
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, "")
                .locations("filesystem:" + migrationDir)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
        System.out.println("FLYWAY_IMPL_VERSION=" + Flyway.class.getPackage().getImplementationVersion());
        var result = flyway.migrate();
        var applied = flyway.info().applied();
        System.out.println("FLYWAY_MIGRATIONS_EXECUTED=" + result.migrationsExecuted);
        System.out.println("FLYWAY_APPLIED_COUNT=" + applied.length);
        for (var info : applied) {
            System.out.println("FLYWAY_APPLIED=" + info.getVersion() + "|" + info.getScript() + "|" + info.getState());
        }
        if (result.migrationsExecuted != 1 || applied.length != 1
                || !"1".equals(applied[0].getVersion().getVersion())
                || !"V1__baseline.sql".equals(applied[0].getScript())) {
            throw new IllegalStateException("Unexpected Flyway result");
        }
    }
}
JAVA

echo "Compiling Flyway runner in isolated Pod"
FLYWAY_COMPILE_CONTAINER="${POD_NAME}-flyway-compile"
set +e
podman run --name "$FLYWAY_COMPILE_CONTAINER" --pod "$POD_NAME" --pull=never --entrypoint javac \
    -v "$FLYWAY_LIB_DIR:/flyway-libs:ro,Z" \
    -v "$RUNTIME_DIR:/runtime:rw,Z" \
    "$JAVA_IMAGE" -cp '/flyway-libs/*' -d /runtime/classes /runtime/FlywayRunner.java >/dev/null
FLYWAY_COMPILE_RC=$?
set -e
podman rm -f "$FLYWAY_COMPILE_CONTAINER" >/dev/null 2>&1 || true
[[ "$FLYWAY_COMPILE_RC" -eq 0 ]] || exit "$FLYWAY_COMPILE_RC"

echo "Running Flyway V1 in isolated Pod"
FLYWAY_RUN_CONTAINER="${POD_NAME}-flyway-run"
set +e
podman run --name "$FLYWAY_RUN_CONTAINER" --pod "$POD_NAME" --pull=never --entrypoint java \
    -v "$FLYWAY_LIB_DIR:/flyway-libs:ro,Z" \
    -v "$RUNTIME_DIR:/runtime:ro,Z" \
    -v "$MIGRATION_DIR:/migration:ro,Z" \
    "$JAVA_IMAGE" -cp '/flyway-libs/*:/runtime/classes' FlywayRunner "$JDBC_URL" "$DB_USER" /migration
FLYWAY_RUN_RC=$?
set -e
podman rm -f "$FLYWAY_RUN_CONTAINER" >/dev/null 2>&1 || true
[[ "$FLYWAY_RUN_RC" -eq 0 ]] || exit "$FLYWAY_RUN_RC"

HISTORY_COUNT="$(podman exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select count(*) from flyway_schema_history")"
HISTORY_SUCCESS="$(podman exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select count(*) from flyway_schema_history where success")"
if [[ "$HISTORY_COUNT" != "1" || "$HISTORY_SUCCESS" != "1" ]]; then
    echo "FAIL: Flyway history must contain exactly one successful V1 row" >&2
    exit 1
fi

RUNTIME_CONFIG="$RUNTIME_DIR/jooq-codegen-1.xml"
sed -e "s#$DB_URL_PLACEHOLDER#$JDBC_URL#g" \
    -e "s#$DB_USER_PLACEHOLDER#$DB_USER#g" \
    -e "s#$DB_AUTH_PLACEHOLDER##g" \
    -e 's#<directory>src/main/java</directory>#<directory>/out</directory>#' \
    "$CODEGEN_CONFIG" > "$RUNTIME_CONFIG"
if grep -q '__JOOQ_' "$RUNTIME_CONFIG"; then
    echo "FAIL: unresolved generator placeholders remain" >&2
    exit 1
fi

run_generation() {
    local config="$1"
    local stage="$2"
    mkdir -p "$stage"
    local gen_name="${POD_NAME}-gen-$(basename "$stage")"
    set +e
    podman run --name "$gen_name" --pod "$POD_NAME" --pull=never --entrypoint java \
        -v "$GENERATOR_LIB_DIR:/generator-libs:ro,Z" \
        -v "$RUNTIME_DIR:/runtime:ro,Z" \
        -v "$stage:/out:rw,Z" \
        "$JAVA_IMAGE" -cp '/generator-libs/*' \
        org.jooq.codegen.GenerationTool "$config"
    local gen_rc=$?
    set -e
    podman rm -f "$gen_name" >/dev/null 2>&1 || true
    return "$gen_rc"
}

echo "Running GenerationTool pass 1"
run_generation "/runtime/jooq-codegen-1.xml" "$STAGING_DIR"

GENERATED_ROOT="$(find "$STAGING_DIR" -type d -path '*/jooq/generated' | head -1)"
if [[ -z "$GENERATED_ROOT" ]]; then
    echo "FAIL: GenerationTool produced no generated source tree" >&2
    exit 1
fi
TABLE_COUNT="$(find "$GENERATED_ROOT/tables" -maxdepth 1 -type f -name '*.java' | wc -l)"
RECORD_COUNT="$(find "$GENERATED_ROOT/tables/records" -maxdepth 1 -type f -name '*.java' | wc -l)"
TOTAL_FILES="$(find "$STAGING_DIR" -type f -name '*.java' | wc -l)"
if [[ "$TABLE_COUNT" -ne 220 || "$RECORD_COUNT" -ne 220 || "$TOTAL_FILES" -ne 445 ]]; then
    echo "FAIL: generation inventory mismatch: table=$TABLE_COUNT record=$RECORD_COUNT total=$TOTAL_FILES" >&2
    exit 1
fi
if find "$STAGING_DIR" -type f -iname '*FlywaySchemaHistory*' | grep -q .; then
    echo "FAIL: FlywaySchemaHistory must not be generated" >&2
    exit 1
fi
for required_file in \
    "$GENERATED_ROOT/tables/WfHostReadiness.java" \
    "$GENERATED_ROOT/tables/WfPhysicalHostDescriptor.java" \
    "$GENERATED_ROOT/tables/WfPhysicalHostDescriptorDevice.java" \
    "$GENERATED_ROOT/tables/WfProviderCandidateSnapshot.java" \
    "$GENERATED_ROOT/tables/records/WfHostReadinessRecord.java" \
    "$GENERATED_ROOT/tables/records/WfPhysicalHostDescriptorRecord.java" \
    "$GENERATED_ROOT/tables/records/WfPhysicalHostDescriptorDeviceRecord.java" \
    "$GENERATED_ROOT/tables/records/WfProviderCandidateSnapshotRecord.java"; do
    [[ -f "$required_file" ]] || { echo "FAIL: required generated file missing: $required_file" >&2; exit 1; }
done
grep -q 'candidate_set_digest' "$GENERATED_ROOT/tables/RenderBindingInputs.java" || { echo "FAIL: candidate_set_digest missing" >&2; exit 1; }
grep -q 'artifact_id' "$GENERATED_ROOT/tables/WfCompletionEvent.java" || { echo "FAIL: artifact_id missing" >&2; exit 1; }
grep -q 'OBSERVED_CPU_UTILIZATION_RATIO' "$GENERATED_ROOT/tables/WfHostResourceSnapshot.java" || { echo "FAIL: host observed fields missing" >&2; exit 1; }
grep -q 'wfCompletionEvent()' "$GENERATED_ROOT/tables/Artifact.java" || { echo "FAIL: Artifact inverse relation missing" >&2; exit 1; }
grep -q 'IX_WF_COMPLETION_EVENT_ARTIFACT_ID' "$GENERATED_ROOT/tables/WfCompletionEvent.java" || { echo "FAIL: WfCompletionEvent index reference missing" >&2; exit 1; }

manifest_stage() {
    local stage="$1"
    local root
    root="$(find "$stage" -type d -path '*/jooq/generated' | head -1)"
    if [[ -z "$root" ]]; then
        echo "FAIL: cannot locate generated package for manifest: $stage" >&2
        return 1
    fi
    (cd "$root" && find . -type f -name '*.java' -print0 | sort -z | xargs -0 sha256sum) > "$stage/manifest.sha256"
}
manifest_stage "$STAGING_DIR"

if [[ "${JOOQ_VERIFY_REPRODUCIBILITY:-1}" != "0" ]]; then
    echo "Running GenerationTool pass 2"
    run_generation "/runtime/jooq-codegen-1.xml" "$STAGING_SECOND"
    manifest_stage "$STAGING_SECOND"
    if ! cmp -s "$STAGING_DIR/manifest.sha256" "$STAGING_SECOND/manifest.sha256"; then
        echo "FAIL: two generation passes are not byte-identical" >&2
        exit 1
    fi
    echo "FORMAL_ENTRY_BYTE_REPRODUCIBLE=YES"
else
    echo "FORMAL_ENTRY_BYTE_REPRODUCIBLE=SKIPPED"
fi

if [[ -n "${JOOQ_EXPECTED_MANIFEST:-}" ]]; then
    if ! cmp -s "$JOOQ_EXPECTED_MANIFEST" "$STAGING_DIR/manifest.sha256"; then
        echo "FAIL: generated manifest does not match expected canonical C manifest" >&2
        diff -u "$JOOQ_EXPECTED_MANIFEST" "$STAGING_DIR/manifest.sha256" | head -100 >&2 || true
        exit 1
    fi
    echo "FORMAL_ENTRY_REGENERATION_MATCHES_C=YES"
else
    echo "FORMAL_ENTRY_REGENERATION_MATCHES_C=NOT_CHECKED"
fi

{
    echo "JOOQ_GENERATION_TOOL_EXECUTED=YES"
    echo "JOOQ_FLYWAY_VERSION=11.20.3"
    echo "JOOQ_TABLE_COUNT=$TABLE_COUNT"
    echo "JOOQ_RECORD_COUNT=$RECORD_COUNT"
    echo "JOOQ_JAVA_COUNT=$TOTAL_FILES"
    echo "JOOQ_STAGING_DIR=$STAGING_DIR"
    echo "JOOQ_STAGING_SECOND_DIR=$STAGING_SECOND"
    echo "JOOQ_MANIFEST=$STAGING_DIR/manifest.sha256"
    echo "JOOQ_TRACKED_TREE_UNCHANGED=YES"
} | tee "$STAGING_DIR/summary.txt"

echo "jOOQ generation complete; staging preserved at $STAGING_DIR"
