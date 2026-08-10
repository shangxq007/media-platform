package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K1 RED absence guards (K1-RED-01..04). Source-scanning: the legacy pre-PRV2
 * capability/runtime skeleton must be ABSENT from the repository's src/main.
 *
 * <p>These are RED on the deleted skeleton (commit 1); they only turn GREEN once
 * the legacy capability family is fully retired and its spring stereotypes are
 * gone from shared-kernel.</p>
 */
class K1RedSharedKernelSkeletonAbsenceTest {

    private static final List<String> KNOWN_RETAINED_SHARED_KERNEL_BEANS = List.of(
            // Legitimate retained infrastructure: loads error-codes.json. NOT part of the
            // capability skeleton. Has ~28 consumers across the platform.
            "com.example.platform.shared.web.ErrorCodeRegistry"
    );

    private Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return p;
    }

    private List<Path> mainJavaFiles(String relDir) {
        Path dir = repoRoot().resolve(relDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/.worktrees/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String stripComments(String source) {
        // Strip block comments first, then line comments.
        String noBlock = source.replaceAll("/\\*.*?\\*/", "");
        return noBlock.replaceAll("//.*", "");
    }

    private long countToken(Path dir, String token) {
        return mainJavaFiles(dir.toString()).stream()
                .mapToLong(f -> {
                    try {
                        String src = stripComments(Files.readString(f));
                        long n = 0;
                        int i = 0;
                        while ((i = src.indexOf(token, i)) >= 0) {
                            n++;
                            i += token.length();
                        }
                        return n;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .sum();
    }

    /** K1-RED-01: legacy AutomationFlow family absent from src/main. */
    @Test
    void k1Red01_automationFlowFamilyAbsent() {
        String tree = "src/main/java";
        assertEquals(0, countToken(repoRoot().resolve(tree), "shared.capability.AutomationFlow"),
                "K1-RED-01: shared.capability.AutomationFlow must be absent from src/main");
        assertEquals(0, countToken(repoRoot().resolve(tree), "AutomationFlowDryRun"),
                "K1-RED-01: AutomationFlowDryRun must be absent from src/main");
        assertEquals(0, countToken(repoRoot().resolve(tree), "AutomationExecutionTrace"),
                "K1-RED-01: AutomationExecutionTrace must be absent from src/main");
        assertEquals(0, countToken(repoRoot().resolve(tree), "AutomationFlowValidation"),
                "K1-RED-01: AutomationFlowValidation must be absent from src/main");
    }

    /** K1-RED-02: legacy capability registries absent from src/main. */
    @Test
    void k1Red02_capabilityRegistriesAbsent() {
        assertEquals(0, countToken(repoRoot().resolve("src/main/java"), "shared.capability.registry"),
                "K1-RED-02: shared.capability.registry must be absent from src/main");
    }

    /** K1-RED-03: duplicate invocation/runtime vocabulary absent from src/main. */
    @Test
    void k1Red03_invocationRuntimeVocabularyAbsent() {
        String tree = "src/main/java";
        assertEquals(0, countToken(repoRoot().resolve(tree), "shared.capability.InvocationContext"),
                "K1-RED-03: shared.capability.InvocationContext must be absent from src/main");
        assertEquals(0, countToken(repoRoot().resolve(tree), "shared.capability.ProviderCapabilities"),
                "K1-RED-03: shared.capability.ProviderCapabilities must be absent from src/main");
        assertEquals(0, countToken(repoRoot().resolve(tree), "shared.capability.ExtensionProvider"),
                "K1-RED-03: shared.capability.ExtensionProvider must be absent from src/main");
    }

    /** K1-RED-04: no application/runtime SKELETON authority in shared-kernel. */
    @Test
    void k1Red04_noSkeletonAuthorityInSharedKernel() {
        Pattern stereotype = Pattern.compile("@(?:Component|Service|RestController|Repository)");
        List<String> nonRetained = mainJavaFiles("shared-kernel/src/main/java").stream()
                .filter(f -> {
                    try {
                        return stereotype.matcher(Files.readString(f)).find();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .map(f -> {
                    String pkg = f.toString().replace(repoRoot().resolve("shared-kernel/src/main/java").toString(), "");
                    // derive FQN crudely from path: strip leading /, drop .java
                    String rel = pkg.startsWith("/") ? pkg.substring(1) : pkg;
                    int dot = rel.lastIndexOf('.');
                    String fqcn = (dot >= 0 ? rel.substring(0, dot) : rel).replace('/', '.');
                    return fqcn;
                })
                .filter(fqcn -> KNOWN_RETAINED_SHARED_KERNEL_BEANS.stream().noneMatch(fqcn::endsWith))
                .toList();
        assertTrue(nonRetained.isEmpty(),
                "K1-RED-04: shared-kernel must contain no skeleton spring beans; unexpected: " + nonRetained);
    }
}
