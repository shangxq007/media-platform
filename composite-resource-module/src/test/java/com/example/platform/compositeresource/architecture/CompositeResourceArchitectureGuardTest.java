package com.example.platform.compositeresource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CompositeResourceArchitectureGuardTest {
    private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
            "(?s)/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    void productionImportsHaveZeroForbiddenAuthorityDependencies() throws IOException {
        Pattern forbiddenImport = Pattern.compile(
                "(?m)^import\\s+com\\.example\\.platform\\."
                        + "(?:studio|timeline|render|storage|artifact|media|operation|workflow|agent|provider|runtime|"
                        + "worker|workerfabric|device)(?:\\.|;)");
        assertZero("forbidden authority imports", matches(mainSources(), forbiddenImport));
    }

    @Test
    void productionHasZeroAuthorityLeakageAndGenericPayloadVocabulary() throws IOException {
        Pattern forbiddenVocabulary = Pattern.compile(
                "\\b(?:OperationTarget|UniversalAsset|CompositeMedia|JsonNode|"
                        + "latest|current|alias|storagePath|storageUri|providerId|providerNative|"
                        + "repository|controller|GraphQL|MCP|Flyway|jOOQ)\\b"
                        + "|Map\\s*<\\s*String\\s*,\\s*Object\\s*>"
                        + "|\\bObject\\s+(?:payload|content|value)\\b");
        assertZero("authority or generic payload vocabulary", matchesStripped(strippedMainSources(), forbiddenVocabulary));
    }

    @Test
    void moduleHasZeroPersistenceTransportFrontendOrPhysicalCoordinateSurface() throws IOException {
        Pattern forbiddenSurface = Pattern.compile(
                "(?i)\\b(?:jdbc|database|schema|migration|flyway|jooq|repository|entity|table|"
                        + "http|graphql|mcp|frontend|controller|endpoint|bucket|objectKey|s3|uri|filesystemPath)\\b");
        assertZero("persistence, public transport, frontend, or physical coordinate surface",
                matchesStripped(strippedMainSources(), forbiddenSurface));
    }

    @Test
    void gradleProductionDependenciesAreExactlySharedKernel() throws IOException {
        String build = Files.readString(moduleRoot().resolve("build.gradle.kts"));
        List<String> productionDependencies = build.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("api(") || line.startsWith("implementation("))
                .toList();
        assertThat(productionDependencies).containsExactly("api(project(\":shared-kernel\"))");
    }

    @Test
    void repositoryContainsOneCompositeResourceVersionAuthorityAndNoReverseImports() throws IOException {
        Pattern authority = Pattern.compile("\\b(?:class|record|interface)\\s+CompositeResourceVersion\\b");
        List<Path> declarations = new ArrayList<>();
        List<Path> reverseImports = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            paths.filter(path -> path.toString().contains("src/main/java"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (authority.matcher(COMMENTS_AND_LITERALS.matcher(source).replaceAll(" ")).find()) {
                                declarations.add(path);
                            }
                            if (!path.startsWith(moduleRoot())
                                    && source.contains("import com.example.platform.compositeresource")) {
                                reverseImports.add(path);
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        assertThat(declarations).as("one CompositeResourceVersion authority").hasSize(1);
        assertThat(reverseImports).as("reverse/private bypass imports").isEmpty();
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(moduleRoot().resolve("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Source> strippedMainSources() throws IOException {
        List<Source> sources = new ArrayList<>();
        for (Path path : mainSources()) {
            sources.add(new Source(path, COMMENTS_AND_LITERALS.matcher(Files.readString(path)).replaceAll(" ")));
        }
        return sources;
    }

    private static List<Source> matches(List<Path> paths, Pattern pattern) throws IOException {
        List<Source> sources = new ArrayList<>();
        for (Path path : paths) {
            String source = Files.readString(path);
            if (pattern.matcher(source).find()) {
                sources.add(new Source(path, source));
            }
        }
        return sources;
    }

    private static List<Source> matchesStripped(List<Source> sources, Pattern pattern) {
        return sources.stream().filter(source -> pattern.matcher(source.content()).find()).toList();
    }

    private static void assertZero(String category, List<Source> violations) {
        assertThat(violations).as(category + " count").isEmpty();
    }

    private static Path moduleRoot() {
        Path working = Path.of("").toAbsolutePath().normalize();
        Path direct = working.resolve("composite-resource-module");
        return Files.isDirectory(direct) ? direct : working;
    }

    private static Path repositoryRoot() {
        Path module = moduleRoot();
        return module.getFileName().toString().equals("composite-resource-module")
                ? module.getParent()
                : module;
    }

    private record Source(Path path, String content) {}
}
