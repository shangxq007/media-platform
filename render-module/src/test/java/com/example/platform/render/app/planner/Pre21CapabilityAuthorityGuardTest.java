package com.example.platform.render.app.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRE-#21 structural guard — capability requirement authority (C4/C5).
 *
 * Mechanically proves the resolver does NOT invent semantic requirements:
 *
 *   RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT = 0
 *   LEGACY_MAPPING_DEFINITION_COUNT = 0
 *   LEGACY_MAPPING_CALL_COUNT = 0
 *   DUAL_REQUIREMENT_AUTHORITY_COUNT = 0
 *
 * Forbidden in CapabilityResolutionService:
 * - mapToCapability method
 * - switch over productType mapping to TaskCapability
 * - resolve(String productType) / explain(String productType) legacy surface
 * - TaskCapability import (outbox coordination enum as semantic authority)
 */
class Pre21CapabilityAuthorityGuardTest {

    private static final String RESOLVER_FILE =
            "render-module/src/main/java/com/example/platform/render/app/planner/CapabilityResolutionService.java";

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return p;
    }

    @Test
    void resolverDoesNotInventCapabilityRequirements() throws IOException {
        Path f = repoRoot().resolve(RESOLVER_FILE);
        assertTrue(Files.exists(f), "CapabilityResolutionService must exist");
        String c = Files.readString(f);

        List<String> violations = new ArrayList<>();
        if (c.contains("mapToCapability")) {
            violations.add("mapToCapability definition must not exist (LEGACY_MAPPING_DEFINITION_COUNT=0)");
        }
        if (c.contains("resolve(String")) {
            violations.add("resolve(String productType) legacy surface must not exist");
        }
        if (c.contains("explain(String")) {
            violations.add("explain(String productType) legacy surface must not exist");
        }
        if (c.contains("TaskCapability")) {
            violations.add("TaskCapability (outbox coordination enum) must not be semantic authority in resolver");
        }
        if (c.contains("switch (productType")) {
            violations.add("productType switch must not invent requirements");
        }
        assertEquals(List.of(), violations,
                "RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT must be 0");
    }

    @Test
    void resolverAcceptsDeclaredCapabilityRequirement() throws IOException {
        String c = Files.readString(repoRoot().resolve(RESOLVER_FILE));
        assertTrue(c.contains("resolve(CapabilityRequirement"),
                "resolver must accept a declared CapabilityRequirement (semantic authority)");
        assertTrue(c.contains("import com.example.platform.extension.domain.CapabilityRequirement;"),
                "resolver must reference the extension CapabilityRequirement type");
    }

    @Test
    void noOtherProductionClassCallsLegacyResolveByProductType() throws IOException {
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path f : walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .toList()) {
                if (f.getFileName().toString().equals("CapabilityResolutionService.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String t = lines.get(i).trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    if (t.contains("capabilityResolver.resolve(") || t.contains("capabilityResolutionService.resolve(")) {
                        violations.add(f.getFileName() + ":" + (i + 1) + ": " + t);
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "LEGACY_MAPPING_CALL_COUNT must be 0 — no production caller of resolver-by-productType");
    }
}
