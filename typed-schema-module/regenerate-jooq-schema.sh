#!/usr/bin/env bash
# Ephemeral PostgreSQL 16 jOOQ codegen script.
# Starts postgres:16-alpine, applies V1__initial_schema.sql, runs jOOQ codegen,
# saves generated sources, stops/removes container.
#
# Usage: ./typed-schema-module/regenerate-jooq-schema.sh
# Requirements: podman (or docker), java, jOOQ codegen JAR

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

SCHEMA_FILE="$ROOT_DIR/platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
OUTPUT_DIR="$SCRIPT_DIR/src/main/java"
TRACKED_GENERATED_DIR="$OUTPUT_DIR/com/example/platform/typedschema/jooq/generated"
CONTAINER_NAME="jooq-codegen-pg16-$(date +%s)-$$"
DB_PORT="15432"
DB_NAME="jooq_codegen"
DB_USER="codegen"
DB_PASS="codegen"

JOOQ_VERSION="3.19.30"
LIB_DIR="${JOOQ_GENERATOR_LIB_DIR:-$SCRIPT_DIR/lib}"
JOOQ_JAR="$LIB_DIR/jooq-codegen-$JOOQ_VERSION.jar"
JOOQ_META_JAR="$LIB_DIR/jooq-meta-$JOOQ_VERSION.jar"
JOOQ_CORE_JAR="$LIB_DIR/jooq-$JOOQ_VERSION.jar"
JAXB_API_JAR="$LIB_DIR/jakarta.xml.bind-api-4.0.4.jar"
REACTIVE_STREAMS_JAR="$LIB_DIR/reactive-streams.jar"
R2DBC_SPI_JAR="$LIB_DIR/r2dbc-spi.jar"
POSTGRES_JDBC_JAR="$LIB_DIR/postgresql-42.7.1.jar"
CODEGEN_CONFIG="$SCRIPT_DIR/jooq-codegen.xml"

required_jars=(
    "$JOOQ_JAR"
    "$JOOQ_META_JAR"
    "$JOOQ_CORE_JAR"
    "$JAXB_API_JAR"
    "$REACTIVE_STREAMS_JAR"
    "$R2DBC_SPI_JAR"
    "$POSTGRES_JDBC_JAR"
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

if [[ ! -f "$SCHEMA_FILE" ]]; then
    echo "FAIL: canonical schema file missing: $SCHEMA_FILE" >&2
    exit 1
fi

if [[ ! -f "$CODEGEN_CONFIG" ]]; then
    echo "FAIL: tracked jOOQ generator config missing: $CODEGEN_CONFIG" >&2
    exit 1
fi

relative_target_count="$(grep -Fc '<directory>src/main/java</directory>' "$CODEGEN_CONFIG" || true)"
if [[ "$relative_target_count" -ne 1 ]]; then
    echo "FAIL: tracked jOOQ generator config must contain exactly one repository-relative target directory" >&2
    exit 1
fi

RUNTIME_DIR=""
container_started=0
cleanup() {
    if [[ "$container_started" -eq 1 ]]; then
        echo "Cleaning up PostgreSQL container..."
        podman stop "$CONTAINER_NAME" 2>/dev/null || true
        podman rm "$CONTAINER_NAME" 2>/dev/null || true
    fi
    if [[ -n "$RUNTIME_DIR" && -d "$RUNTIME_DIR" ]]; then
        rm -rf -- "$RUNTIME_DIR"
    fi
}
trap cleanup EXIT

RUNTIME_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jooq-codegen.XXXXXX")"
RUNTIME_CONFIG="$RUNTIME_DIR/jooq-codegen.xml"
STAGING_SOURCE_DIR="$RUNTIME_DIR/src/main/java"
STAGING_GENERATED_DIR="$STAGING_SOURCE_DIR/com/example/platform/typedschema/jooq/generated"
cp "$CODEGEN_CONFIG" "$RUNTIME_CONFIG"

echo "Starting PostgreSQL 16 container: $CONTAINER_NAME"
podman run -d \
    --name "$CONTAINER_NAME" \
    -e "POSTGRES_DB=$DB_NAME" \
    -e "POSTGRES_USER=$DB_USER" \
    -e "POSTGRES_PASSWORD=$DB_PASS" \
    -p "$DB_PORT:5432" \
    postgres:16-alpine
container_started=1

echo "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
    if podman exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
        echo "PostgreSQL ready after $i seconds"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "FAIL: PostgreSQL did not become ready in 30 seconds"
        exit 1
    fi
    sleep 1
done

echo "Applying V1__initial_schema.sql..."
podman exec -i "$CONTAINER_NAME" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
    < "$SCHEMA_FILE"

echo "Schema applied successfully."

echo "Running jOOQ codegen..."
(
    cd "$RUNTIME_DIR"
    java -cp "$JOOQ_JAR:$JOOQ_META_JAR:$JOOQ_CORE_JAR:$JAXB_API_JAR:$REACTIVE_STREAMS_JAR:$R2DBC_SPI_JAR:$POSTGRES_JDBC_JAR" \
        org.jooq.codegen.GenerationTool \
        "$RUNTIME_CONFIG"
)

if [[ ! -d "$STAGING_GENERATED_DIR/tables/records" ]]; then
    echo "FAIL: GenerationTool produced no complete generated source tree" >&2
    exit 1
fi

TABLE_COUNT="$(find "$STAGING_GENERATED_DIR/tables" -maxdepth 1 -type f -name '*.java' | wc -l)"
RECORD_COUNT="$(find "$STAGING_GENERATED_DIR/tables/records" -maxdepth 1 -type f -name '*.java' | wc -l)"
TOTAL_FILES="$(find "$STAGING_GENERATED_DIR" -type f -name '*.java' | wc -l)"

if [[ "$TABLE_COUNT" -ne 189 || "$RECORD_COUNT" -ne 189 || "$TOTAL_FILES" -ne 383 ]]; then
    echo "FAIL: GenerationTool output inventory mismatch: tables=$TABLE_COUNT records=$RECORD_COUNT java=$TOTAL_FILES" >&2
    exit 1
fi

rm -rf -- "$TRACKED_GENERATED_DIR"
mkdir -p "$(dirname "$TRACKED_GENERATED_DIR")"
cp -a "$STAGING_GENERATED_DIR" "$TRACKED_GENERATED_DIR"

echo "JOOQ_GENERATION_TOOL_EXECUTED=YES"
echo "jOOQ codegen complete."

echo ""
echo "Generated source inventory:"
echo "  Table classes: $TABLE_COUNT"
echo "  Record classes: $RECORD_COUNT"
echo "  Total Java files: $TOTAL_FILES"
