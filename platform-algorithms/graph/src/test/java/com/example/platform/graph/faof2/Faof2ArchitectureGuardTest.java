package com.example.platform.graph.faof2;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class Faof2ArchitectureGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("faof2.repositoryRoot"));

    @Test
    void jgraphT152IsAnExactTestPocWithNoProductionOrDomainLeak() throws Exception {
        Path graphBuild = ROOT.resolve("platform-algorithms/graph/build.gradle.kts");
        List<String> dependencyLines = Files.readAllLines(graphBuild).stream()
                .filter(line -> line.contains("org.jgrapht:jgrapht-core"))
                .map(String::trim)
                .toList();

        assertThat(dependencyLines).containsExactly(
                "testImplementation(\"org.jgrapht:jgrapht-core:1.5.2\")");
        assertThat(productionJavaContaining("org.jgrapht")).isEmpty();

        List<Path> testReferences = javaFilesContaining("org.jgrapht", path ->
                path.toString().contains("/src/test/")
                        && !path.endsWith("Faof2ArchitectureGuardTest.java"));
        assertThat(testReferences).allMatch(path -> normal(path).contains(
                "/platform-algorithms/graph/src/test/java/com/example/platform/graph/faof2/"));
    }

    @Test
    void formalToolingHasNoRuntimeOrGradleModuleDependency() throws Exception {
        assertThat(Files.readString(ROOT.resolve("settings.gradle.kts")))
                .doesNotContain("include(\"formal\")")
                .doesNotContain("include(\":formal\")");

        List<Path> buildLeaks = filesMatching(path -> {
            String name = path.getFileName().toString();
            return name.equals("build.gradle") || name.equals("build.gradle.kts");
        }, text -> text.matches("(?s).*(implementation|api|runtimeOnly)\\s*\\([^\\n]*(lean|coq|formal/).*"));
        assertThat(buildLeaks).isEmpty();
    }

    @Test
    void graphOrderingHasNoObjectRenderingFallback() throws Exception {
        Path graphMain = ROOT.resolve("platform-algorithms/graph/src/main");
        List<Path> fallbacks = javaFilesUnder(graphMain).filter(path -> {
            String text = read(path);
            return text.contains("Object::toString")
                    || text.matches("(?s).*Comparator[^;\\n]*toString.*")
                    || text.matches("(?s).*(TreeSet|PriorityQueue)[^;\\n]*toString.*");
        }).toList();
        assertThat(fallbacks).isEmpty();
    }

    @Test
    void boundedPocContainsNoLaterPhaseImplementation() throws Exception {
        List<Path> scopedSources = new ArrayList<>();
        try (Stream<Path> paths = Stream.concat(
                javaFilesUnder(ROOT.resolve("platform-algorithms/graph/src/main")),
                Files.walk(ROOT.resolve("formal")).filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".lean")
                                || path.toString().endsWith(".v")
                                || path.toString().endsWith(".json")))) {
            scopedSources.addAll(paths.toList());
        }
        String[] forbidden = {"FAOF" + "3", "FAOF-" + "3", "Phase" + "19",
                "Phase " + "19", "ROADMAP_" + "23"};
        for (String marker : forbidden) {
            assertThat(scopedSources.stream().filter(path -> read(path).contains(marker)).toList())
                    .as("forbidden later-phase marker " + marker)
                    .isEmpty();
        }
    }

    private static List<Path> productionJavaContaining(String token) throws IOException {
        return javaFilesContaining(token, path -> path.toString().contains("/src/main/"));
    }

    private static List<Path> javaFilesContaining(String token, Predicate<Path> scope)
            throws IOException {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(scope)
                    .filter(path -> read(path).contains(token))
                    .toList();
        }
    }

    private static List<Path> filesMatching(
            Predicate<Path> pathPredicate, Predicate<String> contentPredicate) throws IOException {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(pathPredicate)
                    .filter(path -> contentPredicate.test(read(path)))
                    .toList();
        }
    }

    private static Stream<Path> javaFilesUnder(Path root) throws IOException {
        return Files.walk(root).filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + normal(path), exception);
        }
    }

    private static String normal(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
