package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AUTH-INVENTORY-FIX-001 — permission vocabulary consistency (action ↔ seed ↔ role link).
 *
 * <p>The RBAC decision port resolves a permission strictly by key: an action key that is not
 * seeded, or seeded but linked to no role, fails closed for every actor (including admins).
 * That is how {@code READ}, {@code CREATE} and {@code workflow.execution.*} drifted. This test
 * makes the vocabulary self-checking from the two real sources of truth — the production seed
 * ({@link BuiltinDataInitializer}) and the permission rows inserted by Flyway migrations.</p>
 *
 * <p>Test working directory is the module root ({@code identity-access-module}).</p>
 */
class AuthPermissionVocabularyConsistencyTest {

    private static final Path INITIALIZER =
            Path.of("src/main/java/com/example/platform/identity/app/BuiltinDataInitializer.java");
    private static final Path MIGRATIONS =
            Path.of("../platform-app/src/main/resources/db/migration");

    private static final Pattern CODE_SEED = Pattern.compile(
            "createPermIfNotExists\\(\\s*\"([^\"]+)\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern CODE_LINK = Pattern.compile(
            "linkRolePermissionIfNotExists\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern MIGRATION_SEED = Pattern.compile(
            "\\('([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'[^']*'\\s*,\\s*'([^']+)'\\s*,\\s*now\\(\\)\\)");

    /**
     * Every action key consumed by live authorization code (an {@code AuthorizationAction}
     * constant or a workflow {@code AuthorizationActions} enum entry). Each must be seeded and
     * linked to at least one role. {@code system.*} keys are intentionally unseeded.
     */
    private static final Set<String> REQUIRED_ACTION_KEYS = Set.of(
            "READ", "WRITE", "CREATE",
            "artifact.read", "artifact.lifecycle.manage",
            "social.read", "social.content.read", "social.artifact.read", "social.publish",
            "delivery.read", "delivery.manage",
            "marketplace.manage", "marketplace.review", "marketplace.publish",
            "workflow-definition.read", "workflow-definition.edit",
            "workflow-definition.publish", "workflow-definition.archive",
            "workflow.execution.start", "workflow.execution.read",
            "workflow.execution.cancel", "workflow.execution.approve");

    /**
     * Seeded vocabulary with no enforcement surface yet. These are allowed to have no role link
     * (documented debt); every other seeded key must be linked, so a new unlinked key fails here.
     */
    private static final Set<String> VOCABULARY_ONLY_UNLINKED = Set.of(
            "render.cancel", "render.use_gpu", "render.use_remote_worker",
            "entitlement.grant", "entitlement.revoke", "billing.manage",
            "prompt.template.manage", "extension.install", "audit.view",
            "navigation.manage", "notification.manage");

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            fail("read failed for " + path + ": " + e.getMessage());
            return "";
        }
    }

    private static Map<String, String> codeSeeds(String initializerSource) {
        Matcher matcher = CODE_SEED.matcher(initializerSource);
        Map<String, String> seeds = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            seeds.put(matcher.group(1), matcher.group(2));
        }
        return seeds;
    }

    private static Map<String, List<String>> codeLinks(String initializerSource) {
        Matcher matcher = CODE_LINK.matcher(initializerSource);
        Map<String, List<String>> links = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            links.computeIfAbsent(matcher.group(2), key -> new ArrayList<>()).add(matcher.group(1));
        }
        return links;
    }

    private static Map<String, String> migrationSeeds() {
        Map<String, String> seeds = new java.util.LinkedHashMap<>();
        if (!Files.isDirectory(MIGRATIONS)) {
            fail("migration directory not found: " + MIGRATIONS.toAbsolutePath());
            return seeds;
        }
        try (Stream<Path> stream = Files.walk(MIGRATIONS)) {
            for (Path sql : stream.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                Matcher matcher = MIGRATION_SEED.matcher(read(sql));
                while (matcher.find()) {
                    seeds.put(matcher.group(2), matcher.group(3));
                }
            }
        } catch (IOException e) {
            fail("migration scan failed: " + e.getMessage());
        }
        return seeds;
    }

    /** The single rule set, factored out so falsification can run it over mutated sources. */
    private static List<String> violations(String initializerSource, Map<String, String> migrationSeeds) {
        List<String> found = new ArrayList<>();
        Map<String, String> seeds = new java.util.LinkedHashMap<>(codeSeeds(initializerSource));
        Set<String> duplicated = new TreeSet<>();
        migrationSeeds.forEach((key, resourceType) -> {
            if (seeds.containsKey(key)) {
                duplicated.add(key);
            }
            seeds.put(key, resourceType);
        });
        if (!duplicated.isEmpty()) {
            found.add("duplicate permission seed (code + migration): " + duplicated);
        }
        seeds.forEach((key, resourceType) -> {
            if (resourceType == null || resourceType.isBlank()) {
                found.add("permission '" + key + "' has no resource_type");
            }
        });

        Map<String, List<String>> links = codeLinks(initializerSource);
        Set<String> linkedKeys = new LinkedHashSet<>(links.keySet());
        for (String linked : linkedKeys) {
            if (!seeds.containsKey(linked)) {
                found.add("role link references an unseeded permission: " + linked);
            }
        }
        for (String required : new TreeSet<>(REQUIRED_ACTION_KEYS)) {
            if (!seeds.containsKey(required)) {
                found.add("authorization action key is not seeded: " + required);
            } else if (!linkedKeys.contains(required)) {
                found.add("authorization action key has no role link: " + required);
            }
        }
        for (String seeded : new TreeSet<>(seeds.keySet())) {
            if (!linkedKeys.contains(seeded) && !VOCABULARY_ONLY_UNLINKED.contains(seeded)) {
                found.add("seeded permission is neither role-linked nor an allowed vocabulary-only key: " + seeded);
            }
        }
        return found;
    }

    private static List<String> currentViolations() {
        return violations(read(INITIALIZER), migrationSeeds());
    }

    // ── baseline ────────────────────────────────────────────────────────────

    @Test
    void everyAuthorizationActionKeyIsSeededAndRoleLinked() {
        List<String> violations = currentViolations();
        assertTrue(violations.isEmpty(),
                "permission vocabulary inconsistency (action/seed/role): " + violations);
    }

    @Test
    void exactRequiredActionKeySetIsPinned() {
        // A new/removed authorization action key must be reflected here deliberately.
        Set<String> pinned = new TreeSet<>(REQUIRED_ACTION_KEYS);
        assertTrue(pinned.containsAll(
                Set.of("CREATE", "workflow.execution.start", "workflow.execution.read",
                        "workflow.execution.cancel", "workflow.execution.approve")),
                "AUTH-INVENTORY-FIX-001 keys must stay in the required action set");
        assertFalse(pinned.contains("system.render.submit"),
                "system.* actions are intentionally unseeded and must not be required here");
    }

    @Test
    void vocabularyOnlyAllowanceStaysExplicitAndDisjoint() {
        Set<String> seeded = new TreeSet<>(codeSeeds(read(INITIALIZER)).keySet());
        seeded.addAll(migrationSeeds().keySet());
        for (String allowed : VOCABULARY_ONLY_UNLINKED) {
            assertTrue(seeded.contains(allowed),
                    "allow-listed vocabulary-only key is not seeded (stale allowance): " + allowed);
            assertFalse(REQUIRED_ACTION_KEYS.contains(allowed),
                    "a required action key must not be allow-listed as vocabulary-only: " + allowed);
        }
    }

    // ── falsification ───────────────────────────────────────────────────────

    @Test
    void removingASeedOrARoleLinkIsDetected() {
        String initializer = read(INITIALIZER);
        Map<String, String> migrations = migrationSeeds();
        assertTrue(violations(initializer, migrations).isEmpty(),
                "baseline must satisfy the vocabulary rule before falsification");

        String seedRemoved = initializer.replace(
                "createPermIfNotExists(\"CREATE\", \"Create project\", \"Create projects within the tenant\", \"PLATFORM\");",
                "");
        assertFalse(seedRemoved.equals(initializer), "falsification anchor (CREATE seed) must exist");
        assertTrue(violations(seedRemoved, migrations).stream()
                        .anyMatch(v -> v.contains("is not seeded: CREATE")),
                "removing the CREATE seed must be detected");

        // workflow.execution.approve has exactly one role link, so removing it must be detected
        // as "no role link" (removing one of several links would leave the key still linked).
        String linkRemoved = initializer.replace(
                "linkRolePermissionIfNotExists(\"ADMIN\", \"workflow.execution.approve\");", "");
        assertFalse(linkRemoved.equals(initializer),
                "falsification anchor (workflow.execution.approve link) must exist");
        assertTrue(violations(linkRemoved, migrations).stream()
                        .anyMatch(v -> v.contains("has no role link: workflow.execution.approve")),
                "removing the workflow.execution.approve role link must be detected");
    }

    @Test
    void duplicateAndOrphanDefinitionsAreDetected() {
        String initializer = read(INITIALIZER);
        // A migration re-defining a code-seeded key is a duplicate.
        Map<String, String> duplicated = new java.util.LinkedHashMap<>(migrationSeeds());
        duplicated.put("CREATE", "PROJECT");
        assertTrue(violations(initializer, duplicated).stream()
                        .anyMatch(v -> v.startsWith("duplicate permission seed")),
                "a duplicate seed must be detected");

        // A role link for an undeclared key is an orphan.
        String orphanLink = initializer + "\n        linkRolePermissionIfNotExists(\"ADMIN\", \"not.seeded.key\");\n";
        assertTrue(violations(orphanLink, migrationSeeds()).stream()
                        .anyMatch(v -> v.contains("unseeded permission: not.seeded.key")),
                "a role link to an unseeded permission must be detected");
    }
}
