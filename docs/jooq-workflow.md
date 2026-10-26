> Current database-test entry: [PostgreSQL 18 test infrastructure](../scripts/testinfra/README.md). Older container/H2 test instructions below are superseded for database tests and do not authorize them. Application startup remains a separate contract.

# jOOQ Code Generation Workflow

## Purpose

This document describes the jOOQ code generation skeleton established in Phase T6.
The goal is to enable type-safe database queries using generated jOOQ classes,
replacing the current raw DSL style (`field("column_name")` strings).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Flyway Migrations (V1__initial_schema.sql ... V9__*.sql)   │
│  ↓                                                          │
│  File-based H2 (MODE=PostgreSQL)                            │
│  ↓                                                          │
│  jOOQ Code Generation (jOOQ CLI / script)                   │
│  ↓                                                          │
│  platform-app/build/generated-sources/jooq/                 │
│    ├── tables/                                              │
│    │   ├── OutboxEvents.java      (table reference)         │
│    │   ├── AuditRecords.java                                │
│    │   └── ...                                               │
│    ├── records/                                             │
│    │   ├── OutboxEventsRecord.java (type-safe record)       │
│    │   └── ...                                               │
│    └── pojos/                                               │
│        ├── OutboxEventsPojo.java  (plain data class)        │
│        └── ...                                               │
└─────────────────────────────────────────────────────────────┘
```

## Generated Code Location

- **Output directory**: `platform-app/build/generated-sources/jooq`
- **Package**: `com.example.platform.jooq.generated`
- **Git**: Excluded via `.gitignore` (do not commit generated code)

## Development Commands

### 1. Generate jOOQ Code

```bash
# Run the jOOQ codegen script (downloads dependencies automatically)
./scripts/generate-jooq.sh

# Prerequisites: Java 25+, curl, network access to Maven Central
```

### 2. Run Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :outbox-event-module:test
```

### 3. Full Development Cycle

```bash
# After modifying migration scripts:
./scripts/generate-jooq.sh    # regenerate jOOQ classes
./gradlew test                # verify everything still passes
```

## jOOQ Plugin Configuration

The jOOQ codegen plugin is declared in the root `build.gradle.kts`:

```kotlin
plugins {
    id("org.jooq.jooq-codegen-gradle") version "3.19.18" apply false
}
```

The plugin is applied `false` (not applied to root project) — it's available for
subprojects or standalone scripts to use.

## Migration Strategy

### Current State (Phase T6)

- All repositories use raw jOOQ DSL: `field("column_name")`, `table("table_name")`
- No type-safe generated classes in use
- Tests pass with H2 in PostgreSQL compatibility mode
- jOOQ codegen script exists at `scripts/generate-jooq.sh`

### Target State (Future Phases)

- New queries use generated jOOQ classes: `OUTBOX_EVENTS.STATUS`, `OUTBOX_EVENTS.ID`
- Existing queries migrate incrementally (no big-bang rewrite)
- Production uses PostgreSQL; tests use H2 with PostgreSQL mode

### Incremental Migration Approach

1. **Phase T6 (current)**: Establish codegen skeleton, script, documentation
2. **Phase T7+**: Run codegen script, add generated sources to classpath, migrate one repository at a time
3. **Final**: All repositories use generated classes; raw DSL only for dynamic queries

## Example: Migrating OutboxEventService.overview()

### Before (raw DSL)
```java
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public Map<String, Object> overview() {
    Integer pending = dsl.fetchCount(
        dsl.selectOne()
            .from(table("outbox_events"))
            .where(field("status").eq(STATUS_PENDING))
    );
    // ...
}
```

### After (generated classes)
```java
import static com.example.platform.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS;

public Map<String, Object> overview() {
    Integer pending = dsl.fetchCount(
        dsl.selectOne()
            .from(OUTBOX_EVENTS)
            .where(OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING))
    );
    // ...
}
```

### Benefits of Generated Classes
- **Type safety**: Column references are compile-time checked
- **Refactoring**: Rename a column → all queries update automatically
- **IDE support**: Autocomplete for table/column names
- **Documentation**: Generated Javadoc from column comments

## Database test execution

Use scripts/test-infra unit or fake with an external new output directory. Fixtures
accept only controlled run Spec/Secret channels, with no H2/container/URL environment
fallback. Live PG18 and target JDBC/Flyway/jOOQ remain separately authorized gates.
See the current infrastructure guide above.

## Module-Specific Notes

### outbox-event-module

- Table: `outbox_events` (14 columns)
- Current queries: CRUD + status transitions + dispatch queries
- Migration priority: Low (stable module, good candidate for first migration)

### audit-compliance-module

- Table: `audit_records` (8 columns)
- Migration candidate after outbox-event-module

### Other Modules

- All modules with database tables are candidates for incremental migration
- Priority: read-heavy repositories first, then write-heve

## Troubleshooting

### jOOQ Codegen Script Fails

If `./scripts/generate-jooq.sh` fails:
1. Check network access to Maven Central
2. Verify Java 25+ is installed: `java -version`
3. Check curl is available: `which curl`
4. Manually download jars to `platform-app/build/libs/`:
   - h2-2.3.232.jar
   - jooq-3.19.18.jar
   - jooq-meta-3.19.18.jar
   - jooq-codegen-3.19.18.jar

### Generated Sources Not Found

After running the codegen script:
1. Verify output exists: `ls platform-app/build/generated-sources/jooq/`
2. Add the generated sources to your IDE's classpath
3. In IntelliJ: Mark directory as "Generated Sources Root"
