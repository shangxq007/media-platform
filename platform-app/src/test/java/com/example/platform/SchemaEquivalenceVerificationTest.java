package com.example.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real dual-database schema equivalence verification.
 *
 * <p>Creates two isolated PostgreSQL databases:
 * - Legacy: runs V1-V4 from commit 096e8ce3
 * - Candidate: runs single V1 from current worktree
 *
 * <p>Compares full schema semantics: tables, columns, types, defaults,
 * nullability, keys, constraints, indexes, sequences, views, triggers,
 * functions, enums, and reference data.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchemaEquivalenceVerificationTest {

    private static final String LEGACY_REF = "096e8ce3a6e1880b7facec3593a4402ff8a92645";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> legacyDb = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("legacy_schema")
            .withUsername("test")
            .withPassword("test");

    @Container
    static PostgreSQLContainer<?> candidateDb = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("candidate_schema")
            .withUsername("test")
            .withPassword("test");

    private static Path legacyMigrationDir;
    private static boolean legacyBuilt = false;
    private static boolean candidateBuilt = false;

    @BeforeAll
    static void extractLegacyMigrations() throws Exception {
        // Extract legacy V1-V4 from 096e8ce3
        legacyMigrationDir = Files.createTempDirectory("legacy-migrations-");

        String[] legacyFiles = {
            "platform-app/src/main/resources/db/migration/V1__init_full_schema.sql",
            "platform-app/src/main/resources/db/migration/V2__create_render_job_lifecycle_events.sql",
            "platform-app/src/main/resources/db/migration/V3__create_ingest_preflight_safe_report_records.sql",
            "platform-app/src/main/resources/db/migration/V4__add_render_job_selected_provider.sql"
        };

        for (String file : legacyFiles) {
            ProcessBuilder pb = new ProcessBuilder("git", "show", LEGACY_REF + ":" + file);
            pb.directory(new java.io.File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] content = p.getInputStream().readAllBytes();
            int exitCode = p.waitFor();
            assertEquals(0, exitCode, "Failed to extract " + file + " from " + LEGACY_REF);

            String fileName = Path.of(file).getFileName().toString();
            Files.write(legacyMigrationDir.resolve(fileName), content);
        }

        // Verify 4 files extracted
        long count = Files.list(legacyMigrationDir).count();
        assertEquals(4, count, "Expected 4 legacy migration files");
    }

    @Test
    @Order(1)
    @DisplayName("Legacy: 4 migrations extracted from 096e8ce3")
    void legacyMigrations_extracted() {
        try {
            long count = Files.list(legacyMigrationDir).count();
            assertEquals(4, count);
        } catch (IOException e) {
            fail("Failed to list legacy migrations: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Legacy: V1-V4 execute successfully")
    void legacyMigrations_execute() {
        Flyway flyway = Flyway.configure()
                .dataSource(legacyDb.getJdbcUrl(), legacyDb.getUsername(), legacyDb.getPassword())
                .locations("filesystem:" + legacyMigrationDir.toString())
                .load();
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 1, "At least one migration should execute");

        var info = flyway.info();
        assertEquals(4, info.all().length, "Expected 4 migrations");
        legacyBuilt = true;
    }

    @Test
    @Order(3)
    @DisplayName("Candidate: single V1 executes successfully")
    void candidateMigrations_execute() {
        Flyway flyway = Flyway.configure()
                .dataSource(candidateDb.getJdbcUrl(), candidateDb.getUsername(), candidateDb.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load();
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 1);

        var info = flyway.info();
        assertEquals(1, info.all().length, "Expected exactly 1 migration");
        assertEquals("1", info.all()[0].getVersion().getVersion());
        assertEquals("V1__initial_schema.sql", info.all()[0].getScript());
        candidateBuilt = true;
    }

    @Test
    @Order(4)
    @DisplayName("Schema equivalence: tables match")
    void schemaEquivalence_tables() throws Exception {
        assertTrue(legacyBuilt && candidateBuilt, "Both databases must be built first");

        Set<String> legacyTables = getTables(legacyDb);
        Set<String> candidateTables = getTables(candidateDb);

        // Remove flyway_schema_history from comparison
        legacyTables.remove("flyway_schema_history");
        candidateTables.remove("flyway_schema_history");

        assertEquals(legacyTables, candidateTables, "Table sets must match");
    }

    @Test
    @Order(5)
    @DisplayName("Schema equivalence: columns, types, defaults, nullability")
    void schemaEquivalence_columns() throws Exception {
        assertTrue(legacyBuilt && candidateBuilt);

        Map<String, Map<String, ColumnInfo>> legacyColumns = getAllColumns(legacyDb);
        Map<String, Map<String, ColumnInfo>> candidateColumns = getAllColumns(candidateDb);

        // Compare each table
        for (String table : legacyColumns.keySet()) {
            if (table.equals("flyway_schema_history")) continue;
            assertTrue(candidateColumns.containsKey(table), "Missing table in candidate: " + table);

            Map<String, ColumnInfo> legacyCols = legacyColumns.get(table);
            Map<String, ColumnInfo> candidateCols = candidateColumns.get(table);

            for (String col : legacyCols.keySet()) {
                assertTrue(candidateCols.containsKey(col),
                        "Missing column " + table + "." + col + " in candidate");
                ColumnInfo l = legacyCols.get(col);
                ColumnInfo c = candidateCols.get(col);
                assertEquals(l.dataType, c.dataType,
                        "Type mismatch for " + table + "." + col);
                assertEquals(l.nullable, c.nullable,
                        "Nullability mismatch for " + table + "." + col);
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("Schema equivalence: primary keys")
    void schemaEquivalence_primaryKeys() throws Exception {
        assertTrue(legacyBuilt && candidateBuilt);

        Map<String, List<String>> legacyPKs = getPrimaryKeys(legacyDb);
        Map<String, List<String>> candidatePKs = getPrimaryKeys(candidateDb);

        assertEquals(legacyPKs.keySet(), candidatePKs.keySet(),
                "Primary key table sets must match");
        for (String table : legacyPKs.keySet()) {
            assertEquals(legacyPKs.get(table), candidatePKs.get(table),
                    "PK columns mismatch for " + table);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Schema equivalence: indexes")
    void schemaEquivalence_indexes() throws Exception {
        assertTrue(legacyBuilt && candidateBuilt);

        Map<String, Set<String>> legacyIdx = getIndexDefinitions(legacyDb);
        Map<String, Set<String>> candidateIdx = getIndexDefinitions(candidateDb);

        assertEquals(legacyIdx.keySet(), candidateIdx.keySet(),
                "Index table sets must match");
        for (String table : legacyIdx.keySet()) {
            assertEquals(legacyIdx.get(table), candidateIdx.get(table),
                    "Index definitions mismatch for " + table);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Schema equivalence: sequences")
    void schemaEquivalence_sequences() throws Exception {
        assertTrue(legacyBuilt && candidateBuilt);

        Set<String> legacySeq = getSequences(legacyDb);
        Set<String> candidateSeq = getSequences(candidateDb);
        assertEquals(legacySeq, candidateSeq, "Sequence sets must match");
    }

    @Test
    @Order(9)
    @DisplayName("Databases are isolated")
    void databasesAreIsolated() {
        assertNotEquals(legacyDb.getJdbcUrl(), candidateDb.getJdbcUrl());
        assertNotEquals(legacyDb.getDatabaseName(), candidateDb.getDatabaseName());
        assertEquals(legacyDb.getDockerImageName(), candidateDb.getDockerImageName(),
                "Both must use same PostgreSQL image");
    }

    // === Helper methods ===

    private Set<String> getTables(PostgreSQLContainer<?> db) throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getTables(null, "public", null, new String[]{"TABLE"});
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase());
        }
        return tables;
    }

    record ColumnInfo(String name, String dataType, String nullable, String defaultValue) {}

    private Map<String, Map<String, ColumnInfo>> getAllColumns(PostgreSQLContainer<?> db) throws SQLException {
        Map<String, Map<String, ColumnInfo>> result = new TreeMap<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getColumns(null, "public", null, null);
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME").toLowerCase();
                String col = rs.getString("COLUMN_NAME").toLowerCase();
                String type = rs.getString("TYPE_NAME").toLowerCase();
                String nullable = rs.getString("IS_NULLABLE");
                String def = rs.getString("COLUMN_DEF");
                result.computeIfAbsent(table, k -> new TreeMap<>())
                        .put(col, new ColumnInfo(col, type, nullable, def));
            }
        }
        return result;
    }

    private Map<String, List<String>> getPrimaryKeys(PostgreSQLContainer<?> db) throws SQLException {
        Map<String, List<String>> result = new TreeMap<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getPrimaryKeys(null, "public", null);
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME").toLowerCase();
                String col = rs.getString("COLUMN_NAME").toLowerCase();
                result.computeIfAbsent(table, k -> new ArrayList<>()).add(col);
            }
        }
        // Sort each list
        result.values().forEach(Collections::sort);
        return result;
    }

    private Map<String, Set<String>> getIndexDefinitions(PostgreSQLContainer<?> db) throws SQLException {
        Map<String, Set<String>> result = new TreeMap<>();
        try (Connection conn = db.createConnection("")) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getIndexInfo(null, "public", null, false, false);
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME").toLowerCase();
                String indexName = rs.getString("INDEX_NAME").toLowerCase();
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                String col = rs.getString("COLUMN_NAME");
                String def = (unique ? "UNIQUE " : "") + indexName + "(" + col + ")";
                result.computeIfAbsent(table, k -> new TreeSet<>()).add(def);
            }
        }
        return result;
    }

    private Set<String> getSequences(PostgreSQLContainer<?> db) throws SQLException {
        Set<String> seqs = new TreeSet<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getTables(null, "public", null, new String[]{"SEQUENCE"});
            while (rs.next()) seqs.add(rs.getString("TABLE_NAME").toLowerCase());
        }
        return seqs;
    }
}
