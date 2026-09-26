package com.example.platform.capability.effective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * AUTH-INVENTORY-FIX-001 — front/back effective-access factor naming consistency.
 *
 * <p>The client projection ({@code frontend/src/foundation/effectiveAccess.tsx}
 * {@code EffectiveAccessFactors}) and the backend effective-capability sources
 * ({@link EffectiveCapabilitySource}) describe the same five factors. The backend keeps its
 * source-authority constant names (pinned by the billing/entitlement convergence guard) and
 * publishes the neutral factor name through {@link EffectiveCapabilitySource#factorKey()};
 * this test pins that vocabulary to the client list so the two cannot drift again.</p>
 *
 * <p>The frontend is fail-closed (a missing server projection yields
 * {@code UNKNOWN_FAIL_CLOSED}); this test changes no behavior, only names.</p>
 */
class EffectiveAccessFactorVocabularyConsistencyTest {

    private static final Path FRONTEND = Path.of("../frontend/src/foundation/effectiveAccess.tsx");
    private static final Pattern FACTOR_BLOCK = Pattern.compile(
            "export interface EffectiveAccessFactors\\s*\\{(.*?)\\n\\}", Pattern.DOTALL);
    private static final Pattern FACTOR_FIELD =
            Pattern.compile("readonly\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:");

    private static final List<String> CANONICAL_FACTORS =
            List.of("capability", "runtime", "entitlement", "quota", "policy");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            fail("read failed for " + path.toAbsolutePath() + ": " + e.getMessage());
            return "";
        }
    }

    private static List<String> backendFactorKeys() {
        return Arrays.stream(EffectiveCapabilitySource.values())
                .map(EffectiveCapabilitySource::factorKey)
                .toList();
    }

    private static List<String> frontendFactorKeys(String source) {
        Matcher block = FACTOR_BLOCK.matcher(source);
        if (!block.find()) {
            fail("frontend EffectiveAccessFactors interface not found");
        }
        List<String> keys = new ArrayList<>();
        Matcher field = FACTOR_FIELD.matcher(block.group(1));
        while (field.find()) {
            keys.add(field.group(1));
        }
        return keys;
    }

    private static List<String> vocabularyViolations(String frontendSource) {
        List<String> found = new ArrayList<>();
        List<String> backend = backendFactorKeys();
        List<String> frontend = frontendFactorKeys(frontendSource);
        if (!backend.equals(frontend)) {
            found.add("backend factorKey() " + backend + " != frontend EffectiveAccessFactors " + frontend);
        }
        if (!backend.equals(CANONICAL_FACTORS)) {
            found.add("backend factorKey() " + backend + " != canonical " + CANONICAL_FACTORS);
        }
        return found;
    }

    @Test
    void backendAndFrontendFactorVocabularyMatchExactly() {
        List<String> violations = vocabularyViolations(read(FRONTEND));
        assertTrue(violations.isEmpty(), "effective-access factor naming drift: " + violations);
    }

    @Test
    void canonicalVocabularyIsCompleteUniqueAndOrdered() {
        List<String> backend = backendFactorKeys();
        assertEquals(CANONICAL_FACTORS, backend);
        assertEquals(5, backend.size(), "the five-factor projection must expose exactly five sources");
        assertEquals(new LinkedHashSet<>(backend).size(), backend.size(),
                "factor keys must be unique");
    }

    @Test
    void removingOrRenamingAFrontendFactorIsDetected() {
        String source = read(FRONTEND);
        assertTrue(vocabularyViolations(source).isEmpty(), "baseline frontend must satisfy the rule");

        String removed = source.replace("  readonly policy: EffectiveAccessFactorStatus\n", "");
        assertNotEquals(source, removed, "falsification anchor (readonly policy) must exist");
        assertFalse(vocabularyViolations(removed).isEmpty(),
                "removing the frontend 'policy' factor must be detected");

        String renamed = source.replace("readonly policy:", "readonly workspacePolicy:");
        assertNotEquals(source, renamed, "falsification anchor (readonly policy) must exist");
        assertFalse(vocabularyViolations(renamed).isEmpty(),
                "renaming the frontend 'policy' factor must be detected");
    }

    @Test
    void renamingABackendFactorKeyIsDetected() {
        List<String> mutated = new ArrayList<>(backendFactorKeys());
        mutated.set(mutated.indexOf("policy"), "workspacePolicy");

        assertNotEquals(backendFactorKeys(), mutated);
        assertNotEquals(mutated, frontendFactorKeys(read(FRONTEND)),
                "a renamed backend factor key must no longer match the client vocabulary");
    }
}
