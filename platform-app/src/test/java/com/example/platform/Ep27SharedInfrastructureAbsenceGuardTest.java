package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Mechanical retirement guard for EP-27 shared serializer and error registry authority. */
class Ep27SharedInfrastructureAbsenceGuardTest {
    private static final String SHARED_PACKAGE = String.join(".", "com", "example", "platform", "shared");
    private static final String SHARED_WEB_PACKAGE = SHARED_PACKAGE + ".web";
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)\\b");

    private static String jsonTypeName() {
        return "Json" + "s";
    }

    private static String registryTypeName() {
        return "Error" + "Code" + "Registry";
    }

    private static String jsonQualifiedName() {
        return SHARED_PACKAGE + "." + jsonTypeName();
    }

    private static String registryQualifiedName() {
        return SHARED_WEB_PACKAGE + "." + registryTypeName();
    }

    private Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("settings.gradle.kts not found");
        return path;
    }

    private List<Path> sourceFiles() {
        try (Stream<Path> paths = Files.walk(root())) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isRepositorySourceFile)
                    .filter(path -> !path.equals(guardFile()))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Path guardFile() {
        return root().resolve(Path.of(
                "platform-app", "src", "test", "java", "com", "example", "platform",
                "Ep27SharedInfrastructureAbsenceGuardTest.java"));
    }

    private boolean isRepositorySourceFile(Path path) {
        Path relative = root().relativize(path);
        boolean sourceTree = false;
        for (int index = 0; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString();
            if (segment.equals("build") || segment.equals(".git") || segment.equals(".worktrees")) return false;
            if (index + 2 < relative.getNameCount()
                    && segment.equals("src")
                    && (relative.getName(index + 1).toString().equals("main")
                    || relative.getName(index + 1).toString().equals("test"))) sourceTree = true;
        }
        return sourceTree;
    }

    private Inspection inspectRepository() {
        return sourceFiles().stream().map(this::inspectFile).reduce(Inspection.EMPTY, Inspection::plus)
                .withDerivedDualAuthority();
    }

    private Inspection inspectFile(Path path) {
        try {
            return inspectSource(Files.readString(path));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Inspection inspectSource(String source) {
        String code = codeOnly(source);
        String packageName = packageName(code);
        RetiredSymbol json = inspectRetiredSymbol(code, packageName, SHARED_PACKAGE, jsonTypeName(), jsonQualifiedName());
        RetiredSymbol registry = inspectRetiredSymbol(
                code, packageName, SHARED_WEB_PACKAGE, registryTypeName(), registryQualifiedName());
        Replacement replacement = inspectReplacementTypes(code);
        long dualAuthority = (json.isPresent() || registry.isPresent()) && replacement.isPresent() ? 1 : 0;
        return new Inspection(json.callers(), json.definitions(), registry.callers(), registry.definitions(),
                replacement.globalJsonUtilities(), replacement.globalErrorRegistries(),
                replacement.compatibilityWrappers(), dualAuthority);
    }

    private static RetiredSymbol inspectRetiredSymbol(
            String code, String packageName, String retiredPackage, String typeName, String qualifiedName) {
        Pattern typeDeclaration = Pattern.compile("\\bclass\\s+(" + Pattern.quote(typeName) + ")\\b");
        long definitions = topLevelMatches(code, typeDeclaration);
        Pattern qualifiedUse = Pattern.compile("(?<![\\w.])" + Pattern.quote(qualifiedName) + "\\b");
        long callers = matches(code, qualifiedUse);
        if (retiredPackage.equals(packageName)) {
            callers += simpleNameReferences(code, typeName, typeDeclaration);
        }
        return new RetiredSymbol(callers, definitions);
    }

    private static Replacement inspectReplacementTypes(String code) {
        long globalJsonUtilities = 0;
        long globalErrorRegistries = 0;
        long compatibilityWrappers = 0;
        Matcher declarations = TYPE_DECLARATION.matcher(code);
        while (declarations.find()) {
            if (braceDepthAt(code, declarations.start()) != 0) continue;
            String typeName = declarations.group(1);
            String normalized = typeName.toLowerCase();
            if (isGlobalJsonUtility(normalized)) globalJsonUtilities++;
            if (isGlobalErrorRegistry(normalized)) globalErrorRegistries++;
            if (isCompatibilitySurface(normalized)) compatibilityWrappers++;
        }
        return new Replacement(globalJsonUtilities, globalErrorRegistries, compatibilityWrappers);
    }

    private static boolean isGlobalJsonUtility(String typeName) {
        boolean unqualifiedGlobalUtility = typeName.equals("jsonutils") || typeName.equals("jsonutility")
                || typeName.equals("jsoncodec");
        return unqualifiedGlobalUtility || hasAuthorityPrefix(typeName)
                && (typeName.contains("json") || typeName.contains("codec") || typeName.contains("objectmapper"));
    }

    private static boolean isGlobalErrorRegistry(String typeName) {
        boolean unqualifiedGlobalRegistry = typeName.equals(registryTypeName().toLowerCase())
                || typeName.equals("errorcatalog");
        return unqualifiedGlobalRegistry || hasAuthorityPrefix(typeName)
                && (typeName.contains("registry") || typeName.contains("catalog"))
                && (typeName.contains("error") || typeName.contains("code"));
    }

    private static boolean hasAuthorityPrefix(String typeName) {
        return typeName.startsWith("global") || typeName.startsWith("shared")
                || typeName.startsWith("platform") || typeName.startsWith("common");
    }

    private static boolean isCompatibilitySurface(String typeName) {
        boolean compatibilityMarker = typeName.contains("compatibility") || typeName.contains("compat")
                || typeName.contains("legacy") || typeName.contains("wrapper")
                || typeName.contains("alias") || typeName.contains("facade");
        boolean retiredConcern = typeName.contains("json") || typeName.contains("error")
                || typeName.contains("registry") || typeName.contains("catalog") || typeName.contains("codec");
        return compatibilityMarker && retiredConcern;
    }

    private static long topLevelMatches(String code, Pattern pattern) {
        return pattern.matcher(code).results().filter(match -> braceDepthAt(code, match.start()) == 0).count();
    }

    private static long simpleNameReferences(String code, String typeName, Pattern typeDeclaration) {
        Pattern name = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b");
        return name.matcher(code).results()
                .filter(match -> !isTypeDeclarationName(match.start(), typeDeclaration, code))
                .count();
    }

    private static boolean isTypeDeclarationName(int position, Pattern typeDeclaration, String code) {
        Matcher declarations = typeDeclaration.matcher(code);
        while (declarations.find()) {
            if (position == declarations.start(1)) return true;
        }
        return false;
    }

    private static long matches(String code, Pattern pattern) {
        return pattern.matcher(code).results().count();
    }

    private static String packageName(String code) {
        Matcher declaration = PACKAGE_DECLARATION.matcher(code);
        return declaration.find() ? declaration.group(1) : "";
    }

    private static int braceDepthAt(String code, int endExclusive) {
        int depth = 0;
        for (int index = 0; index < endExclusive; index++) {
            if (code.charAt(index) == '{') depth++;
            if (code.charAt(index) == '}') depth--;
        }
        return depth;
    }

    /** Replaces comments and literals with whitespace while preserving offsets, braces, and line starts. */
    private static String codeOnly(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean stringLiteral = false;
        boolean characterLiteral = false;
        boolean textBlock = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\n') lineComment = false;
            } else if (blockComment) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '*' && next == '/') {
                    code.append(' ');
                    index++;
                    blockComment = false;
                }
            } else if (textBlock) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
                    code.append("  ");
                    index += 2;
                    textBlock = false;
                }
            } else if (stringLiteral || characterLiteral) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && index + 1 < source.length()) {
                    code.append(source.charAt(index + 1) == '\n' ? '\n' : ' ');
                    index++;
                } else if (current == '"' && stringLiteral) {
                    stringLiteral = false;
                } else if (current == '\'' && characterLiteral) {
                    characterLiteral = false;
                }
            } else if (current == '/' && next == '/') {
                code.append("  ");
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                code.append("  ");
                index++;
                blockComment = true;
            } else if (current == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
                code.append("   ");
                index += 2;
                textBlock = true;
            } else if (current == '"') {
                code.append(' ');
                stringLiteral = true;
            } else if (current == '\'') {
                code.append(' ');
                characterLiteral = true;
            } else {
                code.append(current);
            }
        }
        return code.toString();
    }

    @Test void retiredSharedInfrastructureIsAbsent() {
        Inspection inspection = inspectRepository();
        assertEquals(0, inspection.jsonCallers(), "ZG_EP27_SHARED_JSONS_CALLER_COUNT=0");
        assertEquals(0, inspection.jsonDefinitions(), "ZG_EP27_SHARED_JSONS_DEFINITION_COUNT=0");
        assertEquals(0, inspection.registryCallers(), "ZG_EP27_ERROR_CODE_REGISTRY_CALLER_COUNT=0");
        assertEquals(0, inspection.registryDefinitions(), "ZG_EP27_ERROR_CODE_REGISTRY_DEFINITION_COUNT=0");
        assertEquals(0, inspection.globalJsonUtilities(), "ZG_EP27_GLOBAL_REPLACEMENT_JSON_UTILITY_COUNT=0");
        assertEquals(0, inspection.globalErrorRegistries(), "ZG_EP27_GLOBAL_REPLACEMENT_ERROR_REGISTRY_COUNT=0");
        assertEquals(0, inspection.compatibilityWrappers(), "COMPATIBILITY_WRAPPER_COUNT=0");
        assertEquals(0, inspection.dualAuthority(), "DUAL_AUTHORITY_COUNT=0");
        assertTrue(inspection.isRetired(), "GRD_G_RETIRED=PASS");
    }

    @Test void detectorRejectsRepresentativeReintroductions() {
        Inspection definition = inspectSource("package " + SHARED_PACKAGE + "; class " + jsonTypeName() + " {}");
        Inspection importedUse = inspectSource("package sample; import " + jsonQualifiedName()
                + "; class Sample { Object value = " + jsonTypeName() + ".mapper(); }");
        Inspection constructedUse = inspectSource("package " + SHARED_WEB_PACKAGE + "; class Sample { Object value = new "
                + registryTypeName() + "(); }");
        Inspection globalReplacement = inspectSource("package sample; class " + "PlatformError" + "Code" + "Registry" + " {}");
        Inspection globalJsonReplacement = inspectSource("package sample; class " + "Json" + "Utils" + " {}");
        Inspection dualAuthority = inspectSource("package sample; import " + registryQualifiedName()
                + "; class " + "PlatformError" + "Code" + "Registry" + " {}");
        Inspection commentAndLiteralOnly = inspectSource("// " + registryQualifiedName()
                + "\nclass Sample { String value = \"" + jsonQualifiedName() + "\"; }");

        assertTrue(definition.jsonDefinitions() > 0, "definition mutation must be rejected");
        assertTrue(importedUse.jsonCallers() > 0, "imported caller mutation must be rejected");
        assertTrue(constructedUse.registryCallers() > 0, "same-package construction mutation must be rejected");
        assertTrue(globalReplacement.globalErrorRegistries() > 0, "global replacement mutation must be rejected");
        assertTrue(globalJsonReplacement.globalJsonUtilities() > 0, "global JSON replacement mutation must be rejected");
        assertTrue(dualAuthority.dualAuthority() > 0, "dual authority mutation must be rejected");
        assertTrue(commentAndLiteralOnly.isRetired(), "comments and literals must not be treated as callers");
    }

    private record RetiredSymbol(long callers, long definitions) {
        boolean isPresent() {
            return callers > 0 || definitions > 0;
        }
    }

    private record Replacement(long globalJsonUtilities, long globalErrorRegistries, long compatibilityWrappers) {
        boolean isPresent() {
            return globalJsonUtilities > 0 || globalErrorRegistries > 0 || compatibilityWrappers > 0;
        }
    }

    private record Inspection(
            long jsonCallers,
            long jsonDefinitions,
            long registryCallers,
            long registryDefinitions,
            long globalJsonUtilities,
            long globalErrorRegistries,
            long compatibilityWrappers,
            long dualAuthority) {
        private static final Inspection EMPTY = new Inspection(0, 0, 0, 0, 0, 0, 0, 0);

        Inspection plus(Inspection other) {
            return new Inspection(
                    jsonCallers + other.jsonCallers,
                    jsonDefinitions + other.jsonDefinitions,
                    registryCallers + other.registryCallers,
                    registryDefinitions + other.registryDefinitions,
                    globalJsonUtilities + other.globalJsonUtilities,
                    globalErrorRegistries + other.globalErrorRegistries,
                    compatibilityWrappers + other.compatibilityWrappers,
                    dualAuthority + other.dualAuthority);
        }

        Inspection withDerivedDualAuthority() {
            boolean retiredAuthority = jsonCallers > 0 || jsonDefinitions > 0 || registryCallers > 0 || registryDefinitions > 0;
            boolean replacementAuthority = globalJsonUtilities > 0 || globalErrorRegistries > 0
                    || compatibilityWrappers > 0;
            return new Inspection(
                    jsonCallers,
                    jsonDefinitions,
                    registryCallers,
                    registryDefinitions,
                    globalJsonUtilities,
                    globalErrorRegistries,
                    compatibilityWrappers,
                    retiredAuthority && replacementAuthority ? 1 : 0);
        }

        boolean isRetired() {
            return this.equals(EMPTY);
        }
    }
}
