#!/usr/bin/env bash
# =============================================================================
# jOOQ Code Generation Script
# =============================================================================
# Generates jOOQ Kotlin sources from the database schema.
#
# Prerequisites:
#   - Java 25+ (via SDKMAN or system)
#   - Gradle wrapper (./gradlew)
#
# Usage:
#   ./scripts/generate-jooq.sh
#
# Output:
#   platform-app/build/generated-sources/jooq/
#
# Note: This script requires network access to download jOOQ and H2 jars.
#       If downloads fail, check your network connection and try again.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_DIR="${PROJECT_ROOT}/platform-app/build"
LIBS_DIR="${BUILD_DIR}/libs"
JOOQ_OUTPUT="${BUILD_DIR}/generated-sources/jooq"
JOOQ_VERSION="3.19.18"
H2_VERSION="2.3.232"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# Create directories
mkdir -p "${LIBS_DIR}"
mkdir -p "${JOOQ_OUTPUT}"

# Check for existing jars from previous downloads
H2_JAR=""
JOOQ_JAR=""
JOOQ_META_JAR=""
JOOQ_CODEGEN_JAR=""

# Find any existing H2 jar
for jar in "${LIBS_DIR}"/h2*.jar; do
    [ -f "$jar" ] && H2_JAR="$jar" && break
done

# Find any existing jOOQ jars
for jar in "${LIBS_DIR}"/jooq-${JOOQ_VERSION}.jar "${LIBS_DIR}"/jooq-*.jar; do
    [ -f "$jar" ] && JOOQ_JAR="$jar" && break
done
for jar in "${LIBS_DIR}"/jooq-meta-${JOOQ_VERSION}.jar "${LIBS_DIR}"/jooq-meta-*.jar; do
    [ -f "$jar" ] && JOOQ_META_JAR="$jar" && break
done
for jar in "${LIBS_DIR}"/jooq-codegen-${JOOQ_VERSION}.jar "${LIBS_DIR}"/jooq-codegen-*.jar; do
    [ -f "$jar" ] && JOOQ_CODEGEN_JAR="$jar" && break
done

# Download function
download_if_missing() {
    local url="$1"
    local dest="$2"
    if [ ! -f "${dest}" ]; then
        info "Downloading: ${dest##*/}"
        curl -sL --connect-timeout 15 --max-time 120 "${url}" -o "${dest}" || {
            rm -f "${dest}"
            error "Failed to download ${url}"
        }
    fi
}

# Download all required jars
info "Checking dependencies..."
download_if_missing "https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar" "${H2_JAR:-${LIBS_DIR}/h2-${H2_VERSION}.jar}"
download_if_missing "https://repo1.maven.org/maven2/org/jooq/jooq/${JOOQ_VERSION}/jooq-${JOOQ_VERSION}.jar" "${JOOQ_JAR:-${LIBS_DIR}/jooq-${JOOQ_VERSION}.jar}"
download_if_missing "https://repo1.maven.org/maven2/org/jooq/jooq-meta/${JOOQ_VERSION}/jooq-meta-${JOOQ_VERSION}.jar" "${JOOQ_META_JAR:-${LIBS_DIR}/jooq-meta-${JOOQ_VERSION}.jar}"
download_if_missing "https://repo1.maven.org/maven2/org/jooq/jooq-codegen/${JOOQ_VERSION}/jooq-codegen-${JOOQ_VERSION}.jar" "${JOOQ_CODEGEN_JAR:-${LIBS_DIR}/jooq-codegen-${JOOQ_VERSION}.jar}"

# Re-find jars after download
for jar in "${LIBS_DIR}"/h2*.jar; do [ -f "$jar" ] && H2_JAR="$jar" && break; done
for jar in "${LIBS_DIR}"/jooq-${JOOQ_VERSION}.jar; do [ -f "$jar" ] && JOOQ_JAR="$jar" && break; done
for jar in "${LIBS_DIR}"/jooq-meta-${JOOQ_VERSION}.jar; do [ -f "$jar" ] && JOOQ_META_JAR="$jar" && break; done
for jar in "${LIBS_DIR}"/jooq-codegen-${JOOQ_VERSION}.jar; do [ -f "$jar" ] && JOOQ_CODEGEN_JAR="$jar" && break; done

# Build classpath
CP="${H2_JAR}:${JOOQ_JAR}:${JOOQ_META_JAR}:${JOOQ_CODEGEN_JAR}"

# Use file-based H2 database so migrations and codegen share the same DB
H2_DB_PATH="${BUILD_DIR}/jooq-codegen-db"
H2_DB_URL="jdbc:h2:file:${H2_DB_PATH};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"

# Clean previous database
rm -f "${H2_DB_PATH}".mv.db "${H2_DB_PATH}".trace.db 2>/dev/null || true

# Run migrations
info "Running Flyway migrations on H2 database..."
MIGRATION_DIR="${PROJECT_ROOT}/platform-app/src/main/resources/db/migration"

for sql_file in "${MIGRATION_DIR}"/V*.sql; do
    info "Applying: $(basename "${sql_file}")"
    java -cp "${CP}" org.h2.tools.RunScript \
        -url "${H2_DB_URL}" \
        -user "sa" \
        -password "" \
        -script "${sql_file}" 2>/dev/null || warn "Migration had errors (may be idempotent)"
done

# Create the jOOQ configuration XML
JOOQ_CONFIG="${BUILD_DIR}/jooq-config.xml"
cat > "${JOOQ_CONFIG}" << XMLEOF
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.19.0.xsd">
    <jdbc>
        <driver>org.h2.Driver</driver>
        <url>${H2_DB_URL}</url>
        <user>sa</user>
        <password></password>
    </jdbc>
    <generator>
        <name>org.jooq.codegen.KotlinGenerator</name>
        <database>
            <name>org.jooq.meta.h2.H2Database</name>
            <inputSchema>PUBLIC</inputSchema>
            <includes>.*</includes>
            <excludes>FLYWAY_SCHEMA_HISTORY</excludes>
        </database>
        <generate>
            <pojos>true</pojos>
            <records>true</records>
            <fluentSetters>true</fluentSetters>
            <javaTimeTypes>true</javaTimeTypes>
            <deprecated>false</deprecated>
        </generate>
        <target>
            <packageName>com.example.platform.jooq.generated</packageName>
            <directory>${JOOQ_OUTPUT}</directory>
        </target>
    </generator>
</configuration>
XMLEOF

# Generate jOOQ sources
info "Generating jOOQ Kotlin sources..."
java -cp "${CP}" org.jooq.codegen.GenerationTool "${JOOQ_CONFIG}"

# Clean up
rm -f "${H2_DB_PATH}".mv.db "${H2_DB_PATH}".trace.db 2>/dev/null || true

info ""
info "jOOQ code generation complete!"
info "Generated sources: ${JOOQ_OUTPUT}"
info ""
info "To use the generated classes:"
info "  import com.example.platform.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS"
info "  dsl.selectFrom(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(\"PENDING\"))"
