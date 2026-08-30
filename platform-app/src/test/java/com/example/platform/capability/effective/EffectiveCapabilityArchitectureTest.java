package com.example.platform.capability.effective;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EffectiveCapabilityArchitectureTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path SOURCE = ROOT.resolve(
            "platform-app/src/main/java/com/example/platform/capability/effective");

    @Test
    void projectionIsDocumentedAsNeutralAndOwnsNoSourceAuthority() throws IOException {
        Path packageInfo = SOURCE.resolve("package-info.java");
        assertTrue(Files.isRegularFile(packageInfo));
        String source = Files.readString(packageInfo);
        assertTrue(source.contains("Derived application projection"));
        assertTrue(source.contains("owns no source authority"));
        assertTrue(source.contains("read-only"));
    }

    @Test
    void productionPackageHasNoForbiddenAuthorityOrInfrastructureImports() throws IOException {
        String sources = productionSources();
        List<String> forbiddenImports = List.of(
                "com.example.platform.billing.",
                "com.example.platform.entitlement.",
                "com.example.platform.payment.",
                "com.example.platform.quota.",
                "com.example.platform.render.",
                "com.example.platform.workerfabric.",
                "RuntimeEligibilityEvaluator",
                "RuntimeEligibilityDecision",
                "ProviderCompatibilityGraph",
                "org.springframework",
                "org.jooq",
                "javax.sql",
                "jakarta.persistence");
        forbiddenImports.forEach(token -> assertFalse(
                sources.contains("import " + token), "forbidden import token: " + token));
    }

    @Test
    void projectionContainsNoPersistenceCacheMutationOrWriterConstructs() throws IOException {
        String sources = productionSources();
        Pattern forbidden = Pattern.compile(
                "(?i)\\b(sql|select\\s+.+\\s+from|insert\\s+into|update\\s+.+\\s+set|delete\\s+from|"
                        + "repository|transactional|jdbctemplate|dslcontext|datasource|entitymanager|"
                        + "mutable|concurrenthashmap|hashmap|cacheable|cacheput|cacheevict|"
                        + "save|publish|emit|command|writer)\\b");
        assertFalse(forbidden.matcher(sources).find(), "projection contains a forbidden stateful construct");
        assertFalse(Pattern.compile("\\b(?:double|Double|float|Float)\\b").matcher(sources).find());
    }

    @Test
    void projectionContainsNoPlanTierPaymentOrProviderSelectionSemantics() throws IOException {
        String sources = productionSources();
        List<String> forbidden = List.of(
                "EntitlementPolicyService",
                "ExportCapabilityPolicy",
                "SubscriptionPlan",
                "forTier",
                "PaymentStatus",
                "PAYMENT_FAILED",
                "selectProvider",
                "providerCandidates",
                "ProviderSelection",
                "ProTimeline",
                "EnterpriseRenderGraph",
                "FreeAudioMix",
                "Roadmap #23");
        forbidden.forEach(token -> assertFalse(sources.contains(token), "forbidden semantic token: " + token));
    }

    private static String productionSources() throws IOException {
        try (Stream<Path> files = Files.list(SOURCE)) {
            StringBuilder sources = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String text = Files.readString(file);
                assertTrue(text.contains("package com.example.platform.capability.effective;"));
                sources.append(text).append('\n');
            }
            return sources.toString();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("platform-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
