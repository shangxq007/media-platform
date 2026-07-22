package com.example.platform.typedschema.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JooqUntypedCallGuard}.
 * Uses the 8 test fixtures in resources/guard/fixtures/.
 */
class JooqUntypedCallGuardTest {

    @TempDir
    Path tempDir;

    private Path copyFixture(String resourcePath) throws IOException {
        Path source = Path.of("src/test/resources/guard/fixtures", resourcePath);
        Path target = tempDir.resolve(resourcePath);
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
        return target;
    }

    @Test
    void fixture01_dslTableDetected() throws IOException {
        copyFixture("Fixture01_DslTable.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).callType()).isEqualTo("DSL.table");
    }

    @Test
    void fixture02_dslFieldDetected() throws IOException {
        copyFixture("Fixture02_DslField.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        // Fixture02 has DSL.field(DSL.name(...)) — AST detects both DSL.field and DSL.name
        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(JooqUntypedCallGuard.UntypedCallViolation::callType)
            .contains("DSL.field", "DSL.name");
    }

    @Test
    void fixture03_dslNameDetected() throws IOException {
        copyFixture("Fixture03_DslName.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).callType()).isEqualTo("DSL.name");
    }

    @Test
    void fixture04_multipleCallsDetected() throws IOException {
        copyFixture("Fixture04_MultipleCalls.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        // Fixture04 has DSL.table + DSL.field + DSL.name (inside DSL.field)
        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void fixture05_whitespaceCallDetected() throws IOException {
        copyFixture("Fixture05_DslTableWhitespace.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).callType()).isEqualTo("DSL.table");
    }

    @Test
    void fixture06_inlineFieldDetected() throws IOException {
        copyFixture("Fixture06_DslFieldInline.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).callType()).isEqualTo("DSL.field");
    }

    @Test
    void fixture07_staticContextDetected() throws IOException {
        copyFixture("Fixture07_DslStaticContext.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).callType()).isEqualTo("DSL.table");
    }

    @Test
    void fixture08_generatedTableNotDetected() throws IOException {
        copyFixture("Fixture08_UsesGeneratedTable.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).isEmpty();
    }

    @Test
    void emptyDirectoryReturnsNoViolations() throws IOException {
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scan(tempDir);
        assertThat(violations).isEmpty();
    }

    @Test
    void stableSiteIdFormat() throws IOException {
        Path file = copyFixture("Fixture01_DslTable.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scanFile(file);
        assertThat(violations).hasSize(1);
        String siteId = violations.get(0).stableSiteId();
        assertThat(siteId).contains(":");
        assertThat(siteId).endsWith(":11"); // line 11 has DSL.table
    }
}
