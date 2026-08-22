package com.example.platform.render.app.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 W5 — error algebra ownership validation (C12).
 *
 * Verified ownership model:
 * - domain/orchestration modules own semantic failure categories (module-local
 *   ErrorCode enums: ArtifactErrorCode, ExecutionPlanErrorCode, TimelineError,
 *   OperationErrorCode, ...)
 * - provider adapters map provider-native errors (OpenDalErrorMapper, ...)
 * - API layer owns transport mapping (GlobalExceptionHandler, GraphQLExceptionMapper)
 * - shared ErrorCodeRegistry is a CONFIG-DRIVEN TRANSPORT code registry
 *   (error-codes.json), not a semantic mega authority
 *
 * Guards: no global mega ErrorCode type; no semantic switch inside the
 * registry; provider-native code types stay in provider modules.
 */
class Pre21ErrorAlgebraGuardTest {

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return p;
    }

    private static List<Path> productionJavaFiles() throws IOException {
        Path root = repoRoot();
        boolean rootIsWorktree = root.toString().contains("/.worktrees/");
        Path worktreesDir = rootIsWorktree
                ? root.getParent().getParent().resolve(".worktrees")
                : root.resolve(".worktrees");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.startsWith(worktreesDir) || (rootIsWorktree && f.startsWith(root)))
                    .toList();
        }
    }

    @Test
    void noGlobalMegaErrorCodeAuthorityExists() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.matches("(?:Global|Mega|Universal)ErrorCode.*\\.java")) {
                violations.add(f.toString());
            }
        }
        assertEquals(List.of(), violations,
                "GLOBAL_MEGA_ERROR_CODE_AUTHORITY_COUNT must be 0 — no Global/Mega/Universal ErrorCode type");
    }

    @Test
    void sharedErrorCodeRegistryIsConfigDrivenTransportOnly() throws IOException {
        Path registry = repoRoot().resolve("shared-kernel/src/main/java/com/example/platform/shared/web/ErrorCodeRegistry.java");
        assertTrue(Files.exists(registry));
        String c = Files.readString(registry);
        // must load from config (error-codes.json) — not encode semantic switches
        assertTrue(c.contains("error-codes.json"), "registry must be config-driven");
        assertTrue(c.contains("ConfigurableErrorCode"), "registry holds configurable transport codes");
        assertFalse(c.contains("switch ("), "registry must not contain semantic mapping switches");
    }

    @Test
    void semanticFailureCategoriesAreModuleOwned() throws IOException {
        // each module's ErrorCode enum lives in its own module (no single owner)
        List<String> moduleErrorCodes = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.endsWith("ErrorCode.java") && !name.startsWith("Configurable")) {
                String p = f.toString();
                moduleErrorCodes.add(p.replace(repoRoot() + "/", "").split("/")[0]);
            }
        }
        assertTrue(moduleErrorCodes.size() >= 6,
                "expected multiple module-owned ErrorCode enums, found " + moduleErrorCodes.size());
        assertTrue(moduleErrorCodes.stream().distinct().count() >= 5,
                "error codes must be spread across modules, not centralized");
    }

    @Test
    void providerNativeErrorMappersExistInProviderAdapters() throws IOException {
        boolean openDalMapper = false;
        boolean graphqlMapper = false;
        for (Path f : productionJavaFiles()) {
            String name = f.getFileName().toString();
            if (name.equals("OpenDalErrorMapper.java")) openDalMapper = true;
            if (name.equals("GraphQLExceptionMapper.java")) graphqlMapper = true;
        }
        assertTrue(openDalMapper, "provider-native mapping must exist (OpenDalErrorMapper)");
        assertTrue(graphqlMapper, "API transport mapping must exist (GraphQLExceptionMapper)");
    }

    @Test
    void semanticErrorAuthorityDoesNotImportProviderNativeTypes() throws IOException {
        // RED-6 detector: semantic failure authority layers must not import
        // provider-native packages (storage providers, render providers,
        // outbox coordination). Provider-native codes stay in adapters.
        List<String> violations = new ArrayList<>();
        for (Path f : productionJavaFiles()) {
            String p = f.toString();
            // semantic/domain error authorities: error type files only
            String name = f.getFileName().toString();
            boolean isSemanticAuthority = name.endsWith("ErrorCode.java")
                    || name.endsWith("Errors.java")
                    || (name.endsWith("Error.java") && !name.startsWith("Configurable"))
                    || name.equals("IrErrorCode.java");
            if (!isSemanticAuthority) {
                continue;
            }
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (!t.startsWith("import com.example.platform.")) {
                    continue;
                }
                if (t.contains(".storageprovider.") || t.contains(".provider.")
                        || t.contains("outbox.coordination") || t.contains("infrastructure.")) {
                    violations.add(f.getFileName() + ":" + (i + 1) + ": " + t);
                }
            }
        }
        assertEquals(List.of(), violations,
                "provider-native types must not be imported into semantic failure authority layers");
    }

    @Test
    void extentFailurePathUsesTypedReasonNotFreeTextAuthority() throws IOException {
        // PRE-#21 final exactness: the extent fail-closed path must carry a
        // typed RenderResultFailureReason; the legacy free-text-only
        // failed(String) factory must not exist; hitReason is explanation only.
        Path orchestrator = repoRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/RenderOrchestrator.java");
        assertTrue(Files.exists(orchestrator));
        String c = Files.readString(orchestrator);
        assertTrue(c.contains("RenderResultFailureReason"), "typed failure reason required");
        assertTrue(c.contains("RENDER_EXTENT_UNPROVEN"), "RENDER_EXTENT_UNPROVEN typed category required");
        assertTrue(c.contains("RENDER_EXTENT_NOT_ACHIEVED"), "RENDER_EXTENT_NOT_ACHIEVED typed category required");
        // legacy free-text-authority factory: failed(String jobId, String error)
        // (2-arg form with both Strings) must be absent
        assertFalse(c.contains("failed(String jobId, String error)"),
                "LEGACY_STRING_FAILURE_FACTORY_DEFINITION_COUNT must be 0");
        assertFalse(c.contains("failed(String jobId, String error, RenderExecutionTrace"),
                "LEGACY_STRING_FAILURE_FACTORY_DEFINITION_COUNT must be 0 (trace overload)");
        // no String semantic branching on failure detail
        assertFalse(c.contains("hitReason.contains(") && c.contains("extent"),
                "STRING_FAILURE_SEMANTIC_BRANCH_COUNT must be 0");
    }

    @Test
    void typedFailureReasonHasNoProviderNativeImports() throws IOException {
        Path reason = repoRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/RenderResultFailureReason.java");
        assertTrue(Files.exists(reason), "RenderResultFailureReason must exist");
        String c = Files.readString(reason);
        assertFalse(c.contains("import com.example.platform.outbox"),
                "typed failure reason must not depend on outbox coordination");
        assertFalse(c.contains("import com.example.platform.storage"),
                "typed failure reason must not depend on storage providers");
        assertFalse(c.contains("import com.example.platform.render.infrastructure.provider"),
                "typed failure reason must not depend on provider runtime");
    }
}
