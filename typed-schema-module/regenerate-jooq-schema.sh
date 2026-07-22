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
CONTAINER_NAME="jooq-codegen-pg16-$(date +%s)"
DB_PORT="15432"
DB_NAME="jooq_codegen"
DB_USER="codegen"
DB_PASS="codegen"

JOOQ_VERSION="3.19.30"
JOOQ_JAR="$SCRIPT_DIR/lib/jooq-codegen-$JOOQ_VERSION.jar"
POSTGRES_JDBC_JAR="$SCRIPT_DIR/lib/postgresql-42.7.1.jar"

cleanup() {
    echo "Cleaning up PostgreSQL container..."
    podman stop "$CONTAINER_NAME" 2>/dev/null || true
    podman rm "$CONTAINER_NAME" 2>/dev/null || true
}
trap cleanup EXIT

echo "Starting PostgreSQL 16 container: $CONTAINER_NAME"
podman run -d \
    --name "$CONTAINER_NAME" \
    -e "POSTGRES_DB=$DB_NAME" \
    -e "POSTGRES_USER=$DB_USER" \
    -e "POSTGRES_PASSWORD=$DB_PASS" \
    -p "$DB_PORT:5432" \
    postgres:16-alpine

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
# Generate codegen config with current paths
CODEGEN_CONFIG="$SCRIPT_DIR/jooq-codegen.xml"
cat > "$CODEGEN_CONFIG" << EOF
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.19.0.xsd">
  <jdbc>
    <driver>org.postgresql.Driver</driver>
    <url>jdbc:postgresql://localhost:$DB_PORT/$DB_NAME</url>
    <user>$DB_USER</user>
    <password>$DB_PASS</password>
  </jdbc>
  <generator>
    <name>org.jooq.codegen.JavaGenerator</name>
    <database>
      <name>org.jooq.meta.postgres.PostgresDatabase</name>
      <includes>.*</includes>
      <excludes></excludes>
      <inputSchema>public</inputSchema>
      <forcedTypes>
        <!-- TIMESTAMPTZ → Instant via InstantConverter -->
        <forcedType>
          <userType>java.time.Instant</userType>
          <converter>com.example.platform.typedschema.contract.InstantConverter</converter>
          <includeTypes>.*TIMESTAMPTZ.*</includeTypes>
        </forcedType>
        <!-- TSVECTOR → TsvectorValue via TsvectorBinding -->
        <forcedType>
          <userType>com.example.platform.typedschema.contract.TsvectorValue</userType>
          <binding>com.example.platform.typedschema.contract.TsvectorBinding</binding>
          <includeTypes>.*tsvector.*</includeTypes>
        </forcedType>
      </forcedTypes>
    </database>
    <target>
      <packageName>com.example.platform.typedschema.jooq.generated</packageName>
      <directory>$OUTPUT_DIR</directory>
    </target>
  </generator>
</configuration>
EOF

if [ -f "$JOOQ_JAR" ] && [ -f "$POSTGRES_JDBC_JAR" ]; then
    java -cp "$JOOQ_JAR:$POSTGRES_JDBC_JAR" \
        org.jooq.codegen.GenerationTool \
        "$CODEGEN_CONFIG"
    echo "jOOQ codegen complete."
else
    echo "WARN: jOOQ codegen JARs not found at $SCRIPT_DIR/lib/"
    echo "Expected: $JOOQ_JAR, $POSTGRES_JDBC_JAR"
    echo "Download from Maven Central or run via Gradle."
    echo ""
    echo "To run codegen via Gradle:"
    echo "  ./gradlew :typed-schema-module:dependencies"
    echo "  Then use the resolved classpath to run GenerationTool."
fi

# Count generated files
TABLE_COUNT=$(find "$OUTPUT_DIR" -name "*Table.java" -type f | wc -l)
RECORD_COUNT=$(find "$OUTPUT_DIR" -name "*Record.java" -type f | wc -l)
TOTAL_FILES=$(find "$OUTPUT_DIR" -name "*.java" -type f | wc -l)

echo ""
echo "Generated source inventory:"
echo "  Table classes: $TABLE_COUNT"
echo "  Record classes: $RECORD_COUNT"
echo "  Total Java files: $TOTAL_FILES"
