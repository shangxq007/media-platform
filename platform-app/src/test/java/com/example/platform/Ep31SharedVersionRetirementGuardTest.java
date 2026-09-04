package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Semantic absence guard for the EP-31 shared version/provenance retirement. */
class Ep31SharedVersionRetirementGuardTest {
    private static final String SHARED_VERSION_PACKAGE = "com.example.platform.shared.version";
    private static final List<String> RETIRED = List.of(
            "ExecutionProvenance", "RolloutPolicy", "CompatibilityAdvisory", "ApiContract",
            "CanonicalFormatVersion", "Lifecycle", "ReleaseChannel", "ReleaseVersion", "VersionRange");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)\\b");

    @Test
    void retiredSharedVersionAuthorityIsAbsent() {
        Inspection inspection = inspectRepository();
        RETIRED.forEach(type -> {
            assertEquals(0L, inspection.definitions().get(type), "ZG_EP31_" + type + "_DEFINITION_COUNT=0");
            assertEquals(0L, inspection.callers().get(type), "ZG_EP31_" + type + "_CALLER_COUNT=0");
        });
        assertEquals(0L, inspection.globalVersionReplacements(), "ZG_EP31_GLOBAL_VERSION_REPLACEMENT_COUNT=0");
        assertEquals(0L, inspection.globalProvenanceReplacements(), "ZG_EP31_GLOBAL_PROVENANCE_REPLACEMENT_COUNT=0");
        assertEquals(0L, inspection.compatibilitySurfaces(), "ZG_EP31_COMPATIBILITY_SURFACE_COUNT=0");
        assertTrue(inspection.retired(), "GRD_G_RETIRED");
        assertTrue(inspection.compatibilitySurfaces() == 0, "GRD_V3_COMPATIBILITY");
        assertTrue(operationContractVersionIsIndependent(), "GRD_V3_OPERATION_VERSION");
        assertTrue(providerAndWorkerProvenanceFactsRemainLocal(), "GRD_V3_PROVENANCE");
        assertTrue(inspection.globalVersionReplacements() == 0 && inspection.globalProvenanceReplacements() == 0,
                "GRD_V3_SHARED_VERSION");
    }

    /** Eight in-memory mutation controls; fixtures are never written to the worktree. */
    @Test
    void detectorRejectsRepresentativeReintroductions() {
        Map<String, Inspection> mutations = new LinkedHashMap<>();
        mutations.put("CompatibilityAdvisory", inspectSource(definition("CompatibilityAdvisory")));
        mutations.put("RolloutPolicy", inspectSource(definition("RolloutPolicy")));
        mutations.put("ExecutionProvenance", inspectSource(definition("ExecutionProvenance")));
        mutations.put("ReleaseVersion", inspectSource(definition("ReleaseVersion")));
        mutations.put("retiredFqcnImport", inspectSource("package sample; import " + qualified("ReleaseVersion")
                + "; class Caller { ReleaseVersion value; }"));
        mutations.put("compatibilityAlias", inspectSource("package sample; class LegacyReleaseVersionAlias {}"));
        mutations.put("globalVersionRegistry", inspectSource("package sample; class GlobalVersionRegistry {}"));
        mutations.put("globalExecutionProvenance", inspectSource("package sample; class GlobalExecutionProvenance {}"));
        Inspection reflection = inspectSource("package sample; class Caller { Class<?> type() throws Exception { return "
                + "Class.forName(\"" + qualified("ApiContract") + "\"); } }");
        Inspection commentsAndLiteral = inspectSource("// " + qualified("ApiContract")
                + "\nclass Caller { String value = \"" + qualified("ApiContract") + "\"; }");

        assertTrue(mutations.get("CompatibilityAdvisory").definitions().get("CompatibilityAdvisory") > 0,
                "CompatibilityAdvisory recreation must be rejected");
        assertTrue(mutations.get("RolloutPolicy").definitions().get("RolloutPolicy") > 0,
                "RolloutPolicy recreation must be rejected");
        assertTrue(mutations.get("ExecutionProvenance").definitions().get("ExecutionProvenance") > 0,
                "ExecutionProvenance recreation must be rejected");
        assertTrue(mutations.get("ReleaseVersion").definitions().get("ReleaseVersion") > 0,
                "retired version-value recreation must be rejected");
        assertTrue(mutations.get("retiredFqcnImport").callers().get("ReleaseVersion") > 0,
                "retired FQCN import/reference must be rejected");
        assertTrue(mutations.get("compatibilityAlias").compatibilitySurfaces() > 0,
                "compatibility wrapper/alias must be rejected");
        assertTrue(mutations.get("globalVersionRegistry").globalVersionReplacements() > 0,
                "GlobalVersionRegistry equivalent must be rejected");
        assertTrue(mutations.get("globalExecutionProvenance").globalProvenanceReplacements() > 0,
                "GlobalExecutionProvenance equivalent must be rejected");
        assertTrue(reflection.callers().get("ApiContract") > 0,
                "runtime reflection string must be rejected");
        assertTrue(commentsAndLiteral.retired(), "comments and ordinary string literals are not callers");
    }

    private Inspection inspectRepository() {
        return sourceFiles().stream().map(this::readAndInspect).reduce(Inspection.empty(), Inspection::plus);
    }

    private List<Path> sourceFiles() {
        try (Stream<Path> paths = Files.walk(root())) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isRepositorySource)
                    .filter(path -> !path.equals(guardFile()))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private boolean operationContractVersionIsIndependent() {
        Path operation = root().resolve("operation-module");
        try (Stream<Path> paths = Files.walk(operation)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.getFileName().toString().endsWith(".kts"))
                    .map(this::read)
                    .noneMatch(source -> source.contains("com.example.platform.extension.domain.ContractVersion")
                            || source.contains("project(\":extension-module\")"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private boolean providerAndWorkerProvenanceFactsRemainLocal() {
        return Files.exists(root().resolve("media-execution-plan-module/src/main/java/com/example/platform/execution"))
                && Files.exists(root().resolve("worker-fabric-module/src/main/java/com/example/platform/workerfabric"));
    }

    private Inspection readAndInspect(Path path) {
        return inspectSource(read(path));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Inspection inspectSource(String source) {
        String code = codeOnly(source);
        String packageName = packageName(code);
        Map<String, Long> callers = counts();
        Map<String, Long> definitions = counts();
        for (String type : RETIRED) {
            Pattern declaration = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(" + Pattern.quote(type) + ")\\b");
            if (SHARED_VERSION_PACKAGE.equals(packageName)) {
                long foundDefinitions = declaration.matcher(code).results().count();
                definitions.put(type, foundDefinitions);
                callers.put(type, simpleReferences(code, type, declaration));
            }
            callers.merge(type, matches(code, Pattern.compile("(?<![\\w.])" + Pattern.quote(qualified(type)) + "\\b")), Long::sum);
            callers.merge(type, reflectiveStringReferences(source, qualified(type)), Long::sum);
        }
        Replacement replacement = replacementTypes(code);
        return new Inspection(callers, definitions, replacement.globalVersion(), replacement.globalProvenance(),
                replacement.compatibilitySurface());
    }

    private static Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        RETIRED.forEach(type -> counts.put(type, 0L));
        return counts;
    }

    private static Replacement replacementTypes(String code) {
        long globalVersion = 0;
        long globalProvenance = 0;
        long compatibility = 0;
        Matcher declarations = TYPE.matcher(code);
        while (declarations.find()) {
            String name = declarations.group(1).toLowerCase();
            if (isGlobalVersionReplacement(name)) globalVersion++;
            if (isGlobalProvenanceReplacement(name)) globalProvenance++;
            if (isCompatibilitySurface(name)) compatibility++;
        }
        return new Replacement(globalVersion, globalProvenance, compatibility);
    }

    private static boolean isGlobalVersionReplacement(String name) {
        boolean authority = name.contains("registry") || name.contains("authority") || name.contains("catalog")
                || name.contains("service") || name.contains("policy");
        boolean concern = name.contains("version") || name.contains("release") || name.contains("format")
                || name.contains("contract") || name.contains("lifecycle");
        return (name.equals("versionregistry") || name.equals("releaseversionregistry"))
                || hasGlobalPrefix(name) && authority && concern;
    }

    private static boolean isGlobalProvenanceReplacement(String name) {
        return (name.equals("globalexecutionprovenance") || name.equals("executionprovenanceregistry"))
                || hasGlobalPrefix(name) && name.contains("provenance");
    }

    private static boolean isCompatibilitySurface(String name) {
        boolean marker = name.contains("compat") || name.contains("legacy") || name.contains("wrapper")
                || name.contains("alias") || name.contains("facade") || name.contains("bridge") || name.contains("shim");
        boolean concern = name.contains("version") || name.contains("release") || name.contains("format")
                || name.contains("provenance") || name.contains("rollout") || name.contains("contract")
                || name.contains("lifecycle");
        return marker && concern;
    }

    private static boolean hasGlobalPrefix(String name) {
        return name.startsWith("global") || name.startsWith("shared") || name.startsWith("platform")
                || name.startsWith("common") || name.startsWith("canonical");
    }

    private static long simpleReferences(String code, String type, Pattern declaration) {
        return Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(code).results()
                .filter(match -> !isDeclarationName(match.start(), declaration, code)).count();
    }

    private static boolean isDeclarationName(int index, Pattern declaration, String code) {
        return declaration.matcher(code).results().anyMatch(match -> match.start(1) == index);
    }

    private static long reflectiveStringReferences(String source, String qualifiedName) {
        Pattern runtimeReference = Pattern.compile("(?:Class\\s*\\.\\s*forName|\\.\\s*loadClass)\\s*\\(\\s*\""
                + Pattern.quote(qualifiedName) + "\"");
        return matches(source, runtimeReference);
    }

    private static long matches(String source, Pattern pattern) {
        return pattern.matcher(source).results().count();
    }

    private static String packageName(String code) {
        Matcher matcher = PACKAGE.matcher(code);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("settings.gradle.kts not found");
        return path;
    }

    private Path guardFile() {
        return root().resolve("platform-app/src/test/java/com/example/platform/Ep31SharedVersionRetirementGuardTest.java");
    }

    private boolean isRepositorySource(Path path) {
        Path relative = root().relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            String segment = relative.getName(i).toString();
            if (segment.equals("build") || segment.equals(".git") || segment.equals(".worktrees")) return false;
            if (segment.equals("src") && i + 1 < relative.getNameCount()) {
                String sourceSet = relative.getName(i + 1).toString();
                if (sourceSet.equals("main") || sourceSet.equals("test")) return true;
            }
        }
        return false;
    }

    /** Replaces comments and literals with whitespace while retaining offsets and line breaks. */
    private static String codeOnly(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean line = false, block = false, string = false, character = false, textBlock = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (line) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\n') line = false;
            } else if (block) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '*' && next == '/') { code.append(' '); i++; block = false; }
            } else if (textBlock) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                    code.append("  "); i += 2; textBlock = false;
                }
            } else if (string || character) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && i + 1 < source.length()) { code.append(' '); i++; }
                else if (current == '"' && string) string = false;
                else if (current == '\'' && character) character = false;
            } else if (current == '/' && next == '/') { code.append("  "); i++; line = true; }
            else if (current == '/' && next == '*') { code.append("  "); i++; block = true; }
            else if (current == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                code.append("   "); i += 2; textBlock = true;
            } else if (current == '"') { code.append(' '); string = true; }
            else if (current == '\'') { code.append(' '); character = true; }
            else code.append(current);
        }
        return code.toString();
    }

    private static String definition(String type) {
        return "package " + SHARED_VERSION_PACKAGE + "; public record " + type + "(String value) {}";
    }

    private static String qualified(String type) {
        return SHARED_VERSION_PACKAGE + "." + type;
    }

    private record Replacement(long globalVersion, long globalProvenance, long compatibilitySurface) {}

    private record Inspection(Map<String, Long> callers, Map<String, Long> definitions,
                              long globalVersionReplacements, long globalProvenanceReplacements,
                              long compatibilitySurfaces) {
        static Inspection empty() {
            return new Inspection(counts(), counts(), 0, 0, 0);
        }

        Inspection plus(Inspection other) {
            Map<String, Long> mergedCallers = new LinkedHashMap<>(callers);
            Map<String, Long> mergedDefinitions = new LinkedHashMap<>(definitions);
            RETIRED.forEach(type -> {
                mergedCallers.merge(type, other.callers.get(type), Long::sum);
                mergedDefinitions.merge(type, other.definitions.get(type), Long::sum);
            });
            return new Inspection(mergedCallers, mergedDefinitions,
                    globalVersionReplacements + other.globalVersionReplacements,
                    globalProvenanceReplacements + other.globalProvenanceReplacements,
                    compatibilitySurfaces + other.compatibilitySurfaces);
        }

        boolean retired() {
            return callers.values().stream().allMatch(value -> value == 0)
                    && definitions.values().stream().allMatch(value -> value == 0)
                    && globalVersionReplacements == 0 && globalProvenanceReplacements == 0
                    && compatibilitySurfaces == 0;
        }
    }
}
