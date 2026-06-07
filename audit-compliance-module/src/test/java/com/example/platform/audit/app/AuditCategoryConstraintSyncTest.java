package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Verifies that AuditCategory enum values are kept in sync with the
 * database CHECK constraint in V3__enforce_audit_record_category_constraints.sql.
 *
 * <p>If this test fails, it means a developer added/removed/renamed an
 * AuditCategory enum value but forgot to update the migration file (or vice versa).
 * Both must be updated together.
 */
class AuditCategoryConstraintSyncTest {

    private static final String CONSTRAINT_NAME = "chk_audit_records_category";

    private static final Path MIGRATION_PATH = Path.of(
            "../docs/archive/prelaunch-migrations/V3__enforce_audit_record_category_constraints.sql");

    /**
     * Alternative path when running from the workspace root (Gradle multi-project).
     */
    private static final Path MIGRATION_PATH_ALT = Path.of(
            "../platform-app/src/main/resources/db/migration/V3__enforce_audit_record_category_constraints.sql");

    @Test
    void migrationFileExists() {
        Path path = resolveMigrationPath();
        assertTrue(Files.exists(path),
                "V3 migration file must exist at: " + path.toAbsolutePath());
        assertTrue(Files.isReadable(path),
                "V3 migration file must be readable: " + path.toAbsolutePath());
    }

    @Test
    void constraintNameIsStable() {
        String sql = readMigrationSql();
        assertTrue(sql.contains(CONSTRAINT_NAME),
                "Migration must contain constraint name '" + CONSTRAINT_NAME + "' for stability");
    }

    @Test
    void enumCategoryMatchesDatabaseCheckConstraint() {
        Set<String> enumValues = getEnumValues();
        Set<String> checkValues = parseCheckValues();

        assertEquals(enumValues, checkValues,
                "AuditCategory enum values must exactly match the CHECK constraint values.\n" +
                "  Missing from CHECK (enum has but DB doesn't): " + difference(enumValues, checkValues) + "\n" +
                "  Extra in CHECK (DB has but enum doesn't): " + difference(checkValues, enumValues));
    }

    @Test
    void databaseCheckConstraintDoesNotMissAnyEnumValue() {
        Set<String> enumValues = getEnumValues();
        Set<String> checkValues = parseCheckValues();

        Set<String> missing = difference(enumValues, checkValues);
        assertTrue(missing.isEmpty(),
                "CHECK constraint is missing these AuditCategory values: " + missing +
                "\nAdd them to V3__enforce_audit_record_category_constraints.sql");
    }

    @Test
    void databaseCheckConstraintDoesNotContainUnknownEnumValue() {
        Set<String> enumValues = getEnumValues();
        Set<String> checkValues = parseCheckValues();

        Set<String> extra = difference(checkValues, enumValues);
        assertTrue(extra.isEmpty(),
                "CHECK constraint contains values not in AuditCategory enum: " + extra +
                "\nEither add them to AuditCategory.java or remove from migration");
    }

    @Test
    void databaseCheckConstraintContainsUnknown() {
        Set<String> checkValues = parseCheckValues();
        assertTrue(checkValues.contains("UNKNOWN"),
                "CHECK constraint must contain 'UNKNOWN' for unclassifiable historical records");
    }

    @Test
    void databaseCheckConstraintContainsAdminAudit() {
        Set<String> checkValues = parseCheckValues();
        assertTrue(checkValues.contains("ADMIN_AUDIT"),
                "CHECK constraint must contain 'ADMIN_AUDIT' for admin cross-tenant operations");
    }

    @Test
    void enumContainsUnknown() {
        Set<String> enumValues = getEnumValues();
        assertTrue(enumValues.contains("UNKNOWN"),
                "AuditCategory enum must contain UNKNOWN for unclassifiable records");
    }

    @Test
    void categoryCountIsReasonable() {
        Set<String> enumValues = getEnumValues();
        assertTrue(enumValues.size() >= 15,
                "AuditCategory should have at least 15 values, got: " + enumValues.size());
        assertTrue(enumValues.size() <= 50,
                "AuditCategory should not exceed 50 values, got: " + enumValues.size());
    }

    @Test
    void allEnumValuesAreValidSqlIdentifiers() {
        for (AuditCategory category : AuditCategory.values()) {
            String name = category.name();
            assertTrue(name.matches("^[A-Z][A-Z0-9_]*$"),
                    "AuditCategory value '" + name + "' is not a valid SQL identifier. " +
                    "Must match ^[A-Z][A-Z0-9_]*$");
        }
    }

    // ==================== Helpers ====================

    private Set<String> getEnumValues() {
        return Arrays.stream(AuditCategory.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> parseCheckValues() {
        String sql = readMigrationSql();

        // Match: CHECK (category IN ('A', 'B', 'C', ...))
        Pattern pattern = Pattern.compile(
                "CHECK\\s*\\(\\s*category\\s+IN\\s*\\(\\s*([^)]+)\\s*\\)\\s*\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);

        assertTrue(matcher.find(),
                "Migration must contain CHECK (category IN (...)) constraint");

        String valuesBlock = matcher.group(1);

        // Extract all single-quoted strings
        Pattern valuePattern = Pattern.compile("'([^']+)'");
        Matcher valueMatcher = valuePattern.matcher(valuesBlock);

        Set<String> values = new LinkedHashSet<>();
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }

        assertFalse(values.isEmpty(),
                "CHECK constraint must contain at least one value");

        return values;
    }

    private String readMigrationSql() {
        Path path = resolveMigrationPath();
        try {
            return Files.readString(path);
        } catch (IOException e) {
            fail("Failed to read migration file: " + path.toAbsolutePath() + " — " + e.getMessage());
            return ""; // unreachable
        }
    }

    private Path resolveMigrationPath() {
        if (Files.exists(MIGRATION_PATH)) {
            return MIGRATION_PATH;
        }
        if (Files.exists(MIGRATION_PATH_ALT)) {
            return MIGRATION_PATH_ALT;
        }
        // Try workspace root
        Path workspaceRoot = Path.of("..", "platform-app", "src", "main", "resources", "db", "migration",
                "V3__enforce_audit_record_category_constraints.sql");
        if (Files.exists(workspaceRoot)) {
            return workspaceRoot;
        }
        fail("Cannot find V3 migration file. Searched:\n" +
                "  1. " + MIGRATION_PATH.toAbsolutePath() + "\n" +
                "  2. " + MIGRATION_PATH_ALT.toAbsolutePath() + "\n" +
                "  3. " + workspaceRoot.toAbsolutePath());
        return MIGRATION_PATH; // unreachable
    }

    /**
     * Returns elements in {@code a} that are NOT in {@code b}.
     */
    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }
}
