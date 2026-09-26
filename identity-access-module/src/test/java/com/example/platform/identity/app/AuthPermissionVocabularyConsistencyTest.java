package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AUTH-INVENTORY-FIX-001/FIX-002 — permission vocabulary consistency (action ↔ seed ↔ role link).
 *
 * <p>The RBAC decision port resolves a permission strictly by key: an action key that is not seeded,
 * or seeded but linked to no role, fails closed for every actor (including admins). That is how
 * {@code READ}, {@code CREATE} and {@code workflow.execution.*} drifted.</p>
 *
 * <p>The set of "action keys consumed by live authorization code" is **derived by scanning production
 * sources** (AUTH-INVENTORY-FIX-002 F-1), not typed by hand:</p>
 * <ol>
 *   <li>string literals in the first argument of {@code new AuthorizationAction("…", …)}
 *       (covers direct construction and ternaries such as
 *       {@code new AuthorizationAction(write ? "WRITE" : "READ", …)});</li>
 *   <li>enum-constant keys of any action vocabulary whose constant takes an
 *       {@code AuthorizationResourceType} (the workflow {@code AuthorizationActions} catalogue);</li>
 *   <li>permission literals in {@code access(<scope>, "key")} calls inside a class that consumes
 *       {@code AuthorizationDecisionPort} (the Marketplace authorization helper).</li>
 * </ol>
 * {@code system.*} keys are intentionally unseeded and excluded.
 *
 * <p>{@link #DECLARED_ACTION_KEYS} is the single reviewed source of truth for that vocabulary and is
 * cross-checked against the derived set in BOTH directions, so a new used-but-unseeded key fails here
 * (the exact P0 failure mode) instead of silently denying every caller at runtime.</p>
 *
 * <p>Test working directory is the module root ({@code identity-access-module}).</p>
 */
class AuthPermissionVocabularyConsistencyTest {

    private static final Path REPO_ROOT = Path.of("..");
    private static final Path INITIALIZER =
            Path.of("src/main/java/com/example/platform/identity/app/BuiltinDataInitializer.java");
    private static final Path MIGRATIONS = REPO_ROOT.resolve("platform-app/src/main/resources/db/migration");

    private static final Pattern CODE_SEED = Pattern.compile(
            "createPermIfNotExists\\(\\s*\"([^\"]+)\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern CODE_LINK = Pattern.compile(
            "linkRolePermissionIfNotExists\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern MIGRATION_SEED = Pattern.compile(
            "\\('([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'[^']*'\\s*,\\s*'([^']+)'\\s*,\\s*now\\(\\)\\)");
    /** Enum-constant action vocabulary: {@code NAME("key", AuthorizationResourceType.…)}. */
    private static final Pattern ENUM_ACTION_KEY = Pattern.compile(
            "(?m)^\\s*[A-Z][A-Z0-9_]*\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*AuthorizationResourceType\\.");
    /** Marketplace-style helper call: {@code access(<scope>, "key")}. */
    private static final Pattern ACCESS_HELPER_KEY = Pattern.compile(
            "\\baccess\\s*\\(\\s*[^,()]+,\\s*\"([^\"]+)\"\\s*\\)");
    /** {@code new AuthorizationAction(…)} including fully-qualified constructor names. */
    private static final Pattern ACTION_CALL = Pattern.compile(
            "new\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*AuthorizationAction\\s*\\(");

    /**
     * Reviewed source of truth for the authorization action vocabulary consumed by live code.
     * The test proves it equals the set derived from production sources (both directions).
     */
    private static final Set<String> DECLARED_ACTION_KEYS = Set.of(
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

    // ── source reading ──────────────────────────────────────────────────────

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            fail("read failed for " + path + ": " + e.getMessage());
            return "";
        }
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    private static List<Path> productionJavaSources() {
        if (!Files.isDirectory(REPO_ROOT)) {
            fail("repository root not found: " + REPO_ROOT.toAbsolutePath());
            return List.of();
        }
        List<Path> sources = new ArrayList<>();
        // Prune build/vendor directories: the walk must stay bounded and must not descend into
        // node_modules, .git or generated output.
        Set<String> pruned = Set.of("build", "node_modules", ".git", ".gradle", "dist", "generated");
        try {
            Files.walkFileTree(REPO_ROOT, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(
                        Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null && pruned.contains(name.toString())) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(
                        Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String path = file.toString().replace('\\', '/');
                    if (path.endsWith(".java") && path.contains("/src/main/java/")) {
                        sources.add(file);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            fail("production source scan failed: " + e.getMessage());
            return List.of();
        }
        sources.sort(java.util.Comparator.naturalOrder());
        return sources;
    }

    // ── action-key derivation (F-1) ─────────────────────────────────────────

    /** String literals inside the FIRST argument of every {@code new AuthorizationAction(…)} call. */
    static List<String> directActionKeys(String source) {
        List<String> keys = new ArrayList<>();
        Matcher call = ACTION_CALL.matcher(source);
        while (call.find()) {
            int open = call.end() - 1;
            int depth = 0;
            int end = -1;
            for (int cursor = open; cursor < source.length(); cursor++) {
                char c = source.charAt(cursor);
                if (c == '"') {
                    cursor = skipString(source, cursor);
                    continue;
                }
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = cursor;
                        break;
                    }
                } else if (c == ',' && depth == 1) {
                    end = cursor;
                    break;
                }
            }
            if (end > open) {
                keys.addAll(stringLiterals(source.substring(open + 1, end)));
            }
        }
        return keys;
    }

    private static int skipString(String source, int quoteIndex) {
        int cursor = quoteIndex + 1;
        while (cursor < source.length()) {
            char c = source.charAt(cursor);
            if (c == '\\') {
                cursor += 2;
                continue;
            }
            if (c == '"') {
                return cursor;
            }
            cursor++;
        }
        return source.length() - 1;
    }

    private static List<String> stringLiterals(String text) {
        List<String> literals = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('"', index);
            if (open < 0) {
                break;
            }
            int close = skipString(text, open);
            literals.add(text.substring(open + 1, close));
            index = close + 1;
        }
        return literals;
    }

    static List<String> enumVocabularyKeys(String source) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = ENUM_ACTION_KEY.matcher(source);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    static List<String> accessHelperKeys(String source) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = ACCESS_HELPER_KEY.matcher(source);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** Derives every live action key from production sources, with the finding site for diagnostics. */
    static Map<String, List<String>> deriveActionKeys(Map<String, String> sources) {
        Map<String, List<String>> found = new TreeMap<>();
        sources.forEach((path, raw) -> {
            String source = stripComments(raw);
            boolean consumesDecisionPort = raw.contains("AuthorizationDecisionPort");
            for (String key : directActionKeys(source)) {
                found.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
            }
            for (String key : enumVocabularyKeys(source)) {
                found.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
            }
            if (consumesDecisionPort) {
                for (String key : accessHelperKeys(source)) {
                    found.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
                }
            }
        });
        // SYSTEM-only actions are intentionally unseeded (RbacAuthorizationDecisionPort allows any
        // "system." key for ActorType.SYSTEM explicitly).
        found.keySet().removeIf(key -> key.startsWith("system."));
        return found;
    }

    private static Map<String, String> productionSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path path : productionJavaSources()) {
            sources.put(REPO_ROOT.relativize(path).toString(), read(path));
        }
        return sources;
    }

    // ── seeds / links ───────────────────────────────────────────────────────

    private static Map<String, String> codeSeeds(String initializerSource) {
        Matcher matcher = CODE_SEED.matcher(initializerSource);
        Map<String, String> seeds = new LinkedHashMap<>();
        while (matcher.find()) {
            seeds.put(matcher.group(1), matcher.group(2));
        }
        return seeds;
    }

    private static Map<String, List<String>> codeLinks(String initializerSource) {
        Matcher matcher = CODE_LINK.matcher(initializerSource);
        Map<String, List<String>> links = new LinkedHashMap<>();
        while (matcher.find()) {
            links.computeIfAbsent(matcher.group(2), key -> new ArrayList<>()).add(matcher.group(1));
        }
        return links;
    }

    private static Map<String, String> migrationSeeds() {
        Map<String, String> seeds = new LinkedHashMap<>();
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

    // ── the rule set, factored for falsification ────────────────────────────

    static List<String> violations(
            Map<String, List<String>> liveActionKeys,
            String initializerSource,
            Map<String, String> migrationSeeds) {
        List<String> found = new ArrayList<>();
        Map<String, String> seeds = new LinkedHashMap<>(codeSeeds(initializerSource));
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

        // The derived usage set and the reviewed declaration must agree in both directions; a new
        // used-but-undeclared key (the P0 failure mode) fails here.
        Set<String> derived = new TreeSet<>(liveActionKeys.keySet());
        Set<String> declared = new TreeSet<>(DECLARED_ACTION_KEYS);
        if (!derived.equals(declared)) {
            Set<String> undeclared = new TreeSet<>(derived);
            undeclared.removeAll(declared);
            Set<String> unused = new TreeSet<>(declared);
            unused.removeAll(derived);
            found.add("authorization action keys used in production but not declared: " + undeclared);
            found.add("declared authorization action keys not used in production: " + unused);
        }
        for (String key : derived) {
            if (!seeds.containsKey(key)) {
                found.add("authorization action key is not seeded: " + key
                        + " (used at " + liveActionKeys.get(key) + ")");
            } else if (!linkedKeys.contains(key)) {
                found.add("authorization action key has no role link: " + key);
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
        return violations(deriveActionKeys(productionSources()), read(INITIALIZER), migrationSeeds());
    }

    // ── baseline ────────────────────────────────────────────────────────────

    @Test
    void everyProductionActionKeyIsSeededRoleLinkedAndDeclared() {
        List<String> violations = currentViolations();
        assertTrue(violations.isEmpty(),
                "permission vocabulary inconsistency (action/seed/role): " + violations);
    }

    @Test
    void derivationFindsTheKnownActionVocabularies() {
        Map<String, List<String>> derived = deriveActionKeys(productionSources());
        // Sanity: the scanner must see the three shapes (direct construction, enum catalogue,
        // authorization-helper call) — otherwise the derivation silently degrades to "no keys".
        assertTrue(derived.containsKey("READ"),
                "derived set must include directly constructed action keys");
        assertTrue(derived.containsKey("workflow.execution.start"),
                "derived set must include enum-catalogue action keys (AuthorizationActions)");
        assertTrue(derived.containsKey("marketplace.manage"),
                "derived set must include authorization-helper action keys (Marketplace)");
        assertTrue(derived.keySet().stream().noneMatch(key -> key.startsWith("system.")),
                "system.* actions are intentionally unseeded and must be excluded");
    }

    @Test
    void vocabularyOnlyAllowanceStaysExplicitAndDisjoint() {
        Set<String> seeded = new TreeSet<>(codeSeeds(read(INITIALIZER)).keySet());
        seeded.addAll(migrationSeeds().keySet());
        for (String allowed : VOCABULARY_ONLY_UNLINKED) {
            assertTrue(seeded.contains(allowed),
                    "allow-listed vocabulary-only key is not seeded (stale allowance): " + allowed);
            assertFalse(DECLARED_ACTION_KEYS.contains(allowed),
                    "a required action key must not be allow-listed as vocabulary-only: " + allowed);
        }
    }

    // ── falsification ───────────────────────────────────────────────────────

    @Test
    void scannerExtractsKeysFromSourceText() {
        // Proves the derivation is text-driven: an injected unseeded action key is extracted.
        String injected = "class X { void f(){ port.requireAuthorized(new AuthorizationAction("
                + "\"brand.new.project.key\", AuthorizationResourceType.PROJECT, \"New\")); } }";
        assertTrue(directActionKeys(injected).contains("brand.new.project.key"),
                "directActionKeys must extract the injected key");

        String ternary = "new AuthorizationAction(write ? \"WRITE\" : \"brand.ternary.key\", T, \"n\")";
        List<String> ternaryKeys = directActionKeys(ternary);
        assertTrue(ternaryKeys.contains("WRITE") && ternaryKeys.contains("brand.ternary.key"),
                "directActionKeys must extract both ternary literals: " + ternaryKeys);

        String enumText = "    BRAND_NEW(\"brand.enum.key\", AuthorizationResourceType.PROJECT, \"n\"),\n";
        assertTrue(enumVocabularyKeys(enumText).contains("brand.enum.key"),
                "enumVocabularyKeys must extract an enum-catalogue key");

        String helperText = "    return access(project,\"brand.helper.key\");\n";
        assertTrue(accessHelperKeys(helperText).contains("brand.helper.key"),
                "accessHelperKeys must extract an authorization-helper key");
    }

    @Test
    void aNewUnseededActionKeyUsageIsDetected() {
        String initializer = read(INITIALIZER);
        Map<String, String> migrations = migrationSeeds();
        Map<String, List<String>> derived = deriveActionKeys(productionSources());
        assertTrue(violations(derived, initializer, migrations).isEmpty(),
                "baseline must satisfy the vocabulary rule before falsification");

        // Inject a live-but-unseeded, undeclared action key exactly as a new surface would.
        Map<String, List<String>> injected = new LinkedHashMap<>(derived);
        injected.put("brand.new.project.key", List.of("<injected by falsification>"));
        List<String> injectedViolations = violations(injected, initializer, migrations);
        assertTrue(injectedViolations.stream().anyMatch(v -> v.contains("brand.new.project.key")),
                "a new used-but-unseeded action key must be detected: " + injectedViolations);
        assertTrue(injectedViolations.stream()
                        .anyMatch(v -> v.startsWith("authorization action keys used in production but not declared")),
                "the undeclared-key rule must fire: " + injectedViolations);
    }

    @Test
    void removingASeedOrTheSingleRoleLinkOfAnActionKeyIsDetected() {
        String initializer = read(INITIALIZER);
        Map<String, String> migrations = migrationSeeds();
        Map<String, List<String>> derived = deriveActionKeys(productionSources());

        String seedRemoved = initializer.replace(
                "createPermIfNotExists(\"CREATE\", \"Create project\", \"Create projects within the tenant\", \"PLATFORM\");",
                "");
        assertFalse(seedRemoved.equals(initializer), "falsification anchor (CREATE seed) must exist");
        assertTrue(violations(derived, seedRemoved, migrations).stream()
                        .anyMatch(v -> v.contains("is not seeded: CREATE")),
                "removing the CREATE seed must be detected");

        // workflow.execution.approve has exactly one role link, so removing it is detectable as
        // "no role link" (removing one of several links would leave the key still linked).
        String linkRemoved = initializer.replace(
                "linkRolePermissionIfNotExists(\"ADMIN\", \"workflow.execution.approve\");", "");
        assertFalse(linkRemoved.equals(initializer),
                "falsification anchor (workflow.execution.approve link) must exist");
        assertTrue(violations(derived, linkRemoved, migrations).stream()
                        .anyMatch(v -> v.contains("has no role link: workflow.execution.approve")),
                "removing the workflow.execution.approve role link must be detected");
    }

    @Test
    void duplicateAndOrphanDefinitionsAreDetected() {
        String initializer = read(INITIALIZER);
        Map<String, List<String>> derived = deriveActionKeys(productionSources());

        Map<String, String> duplicated = new LinkedHashMap<>(migrationSeeds());
        duplicated.put("CREATE", "PROJECT");
        assertTrue(violations(derived, initializer, duplicated).stream()
                        .anyMatch(v -> v.startsWith("duplicate permission seed")),
                "a duplicate seed must be detected");

        String orphanLink = initializer + "\n        linkRolePermissionIfNotExists(\"ADMIN\", \"not.seeded.key\");\n";
        assertTrue(violations(derived, orphanLink, migrationSeeds()).stream()
                        .anyMatch(v -> v.contains("unseeded permission: not.seeded.key")),
                "a role link to an unseeded permission must be detected");
    }
}
