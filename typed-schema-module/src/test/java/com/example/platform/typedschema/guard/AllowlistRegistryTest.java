package com.example.platform.typedschema.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AllowlistRegistry}.
 * Covers load, save, filter, and duplicate detection.
 */
class AllowlistRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReturnsEmptySetWhenFileMissing() throws IOException {
        Path missing = tempDir.resolve("nonexistent.txt");
        Set<String> result = AllowlistRegistry.load(missing);
        assertThat(result).isEmpty();
    }

    @Test
    void loadParsesSiteIds() throws IOException {
        Path file = tempDir.resolve("allowlist.txt");
        Files.write(file, List.of(
            "# comment line",
            "src/Main.java:10",
            "src/Other.java:20"
        ));
        Set<String> result = AllowlistRegistry.load(file);
        assertThat(result).containsExactly("src/Main.java:10", "src/Other.java:20");
    }

    @Test
    void loadHandlesPipeDelimitedFormat() throws IOException {
        Path file = tempDir.resolve("allowlist.txt");
        Files.write(file, List.of(
            "src/Main.java:10|src/Main.java|1"
        ));
        Set<String> result = AllowlistRegistry.load(file);
        assertThat(result).containsExactly("src/Main.java:10");
    }

    @Test
    void saveWritesHeaderAndSiteIds() throws IOException {
        Path file = tempDir.resolve("output.txt");
        Set<String> ids = new LinkedHashSet<>(Set.of("a.java:1", "b.java:2"));
        AllowlistRegistry.save(file, ids, "Header");
        List<String> lines = Files.readAllLines(file);
        assertThat(lines.get(0)).startsWith("#");
        assertThat(lines).contains("a.java:1", "b.java:2");
    }

    @Test
    void filterViolationsRemovesAllowed() throws IOException {
        Path file = copyFixture("Fixture01_DslTable.java");
        List<JooqUntypedCallGuard.UntypedCallViolation> violations =
            JooqUntypedCallGuard.scanFile(file);
        assertThat(violations).isNotEmpty();

        Set<String> allowed = Set.of(violations.get(0).stableSiteId());
        List<JooqUntypedCallGuard.UntypedCallViolation> filtered =
            AllowlistRegistry.filterViolations(violations, allowed);
        assertThat(filtered).isEmpty();
    }

    @Test
    void checkNoDuplicatesReturnsTrueForCleanFile() throws IOException {
        Path file = tempDir.resolve("clean.txt");
        Files.write(file, List.of("a.java:1", "b.java:2"));
        assertThat(AllowlistRegistry.checkNoDuplicates(file)).isTrue();
    }

    @Test
    void checkNoDuplicatesReturnsFalseForDuplicates() throws IOException {
        Path file = tempDir.resolve("dup.txt");
        Files.write(file, List.of("a.java:1", "a.java:1"));
        assertThat(AllowlistRegistry.checkNoDuplicates(file)).isFalse();
    }

    private Path copyFixture(String name) throws IOException {
        Path source = Path.of("src/test/resources/guard/fixtures", name);
        Path target = tempDir.resolve(name);
        Files.copy(source, target);
        return target;
    }
}
