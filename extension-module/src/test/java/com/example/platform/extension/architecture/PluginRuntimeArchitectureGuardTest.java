package com.example.platform.extension.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PRV2-FV1 architecture guards AR-PRV2-01..20 as deterministic source-boundary
 * assertions (repository convention: source scans, not ArchUnit).
 *
 * <p>Definitions frozen in PRV2-ARSF evidence guards/guards.json.</p>
 */
class PluginRuntimeArchitectureGuardTest {

    // Test working directory is extension-module root.
    private static final Path RUNTIME =
            Path.of("src/main/java/com/example/platform/extension/runtime");
    private static final Path RUNTIME_INTERNAL =
            Path.of("src/main/java/com/example/platform/extension/runtime/internal");
    private static final Path REGISTRY =
            Path.of("src/main/java/com/example/platform/extension/app");
    private static final Path AI =
            Path.of("../ai-module/src/main/java/com/example/platform/ai");
    private static final Path RENDER =
            Path.of("../render-module/src/main/java/com/example/platform/render");

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            fail("cannot read " + p + ": " + e.getMessage());
            return "";
        }
    }

    /** Strips // and /* * / comments so guard assertions scan code, not prose. */
    private static String stripComments(String src) {
        String s = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        return s.replaceAll("//[^\n]*", " ");
    }

    private static boolean anyFileContains(Path root, Pattern pattern) throws IOException {
        for (Path p : javaFiles(root)) {
            if (pattern.matcher(read(p)).find()) {
                return true;
            }
        }
        return false;
    }

    // --- AR-PRV2-01: Capability Registry does not execute providers ---
    @org.junit.jupiter.api.Test
    void arPrv2_01_registryDoesNotExecute() throws IOException {
        // registry MATCHING/SELECTION classes (PluginMatcher, PluginRegistryImpl) must
        // not invoke provider execution; ExtensionRegistryService is the execution
        // facade (Compatibility Model B) and delegates to the runtime adapter.
        for (Path p : javaFiles(REGISTRY)) {
            String name = p.getFileName().toString();
            if (!name.contains("Matcher") && !name.contains("RegistryImpl") && !name.contains("PluginRegistryPort")) {
                continue;
            }
            String src = stripComments(read(p));
            assertFalse(src.contains(".execute(context,"),
                    "AR-PRV2-01 registry matcher/impl must not execute: " + p);
            assertFalse(src.contains("sandboxExecutionService.executeExtension"),
                    "AR-PRV2-01 registry matcher/impl must not call engine: " + p);
        }
    }

    // --- AR-PRV2-02: Runtime does not own domain lifecycles ---
    @org.junit.jupiter.api.Test
    void arPrv2_02_runtimeDoesNotOwnDomainLifecycles() throws IOException {
        Pattern domainLifecycle = Pattern.compile(
                "RenderJobService|DeliveryJobService|PublicationService|WorkflowDefinitionService|BillingLedgerService");
        assertFalse(anyFileContains(RUNTIME, domainLifecycle),
                "AR-PRV2-02 runtime must not reference domain lifecycle services");
    }

    // --- AR-PRV2-03: Untrusted code cannot use TRUSTED_IN_PROCESS (GAP-003) ---
    @org.junit.jupiter.api.Test
    void arPrv2_03_untrustedCannotTrustedInProcess() throws IOException {
        // TrustPolicyEnforcer must contain the denial rule
        Path enforcer = RUNTIME_INTERNAL.resolve("TrustPolicyEnforcer.java");
        assertTrue(Files.exists(enforcer), "AR-PRV2-03 TrustPolicyEnforcer missing");
        String src = read(enforcer);
        assertTrue(src.contains("SECURITY_DENIED"), "AR-PRV2-03 must deny with SECURITY_DENIED");
        assertTrue(src.contains("ISOLATION_REQUIRED"), "AR-PRV2-03 trust classification missing");
    }

    // --- AR-PRV2-04: Runtime request requires tenant ---
    @org.junit.jupiter.api.Test
    void arPrv2_04_tenantRequired() throws IOException {
        Path req = RUNTIME.resolve("PluginExecutionRequest.java");
        String src = read(req);
        assertTrue(src.contains("tenantId must not be blank"), "AR-PRV2-04 tenant validation missing");
    }

    // --- AR-PRV2-05: Runtime uses CanonicalActor semantics ---
    @org.junit.jupiter.api.Test
    void arPrv2_05_canonicalActorUsed() throws IOException {
        for (Path p : javaFiles(RUNTIME)) {
            String src = stripComments(read(p));
            assertFalse(src.contains("SecurityContext"), "AR-PRV2-05 SecurityContext in " + p);
            assertFalse(src.contains("Authentication"), "AR-PRV2-05 Authentication in " + p);
        }
        Path req = RUNTIME.resolve("PluginExecutionRequest.java");
        assertTrue(read(req).contains("CanonicalActorRef"), "AR-PRV2-05 CanonicalActorRef missing");
    }

    // --- AR-PRV2-06: Secrets are referenced, not serialized ---
    @org.junit.jupiter.api.Test
    void arPrv2_06_secretsReferencedNotSerialized() throws IOException {
        for (Path p : javaFiles(RUNTIME)) {
            String src = read(p);
            assertFalse(Pattern.compile("(apiKey|secretValue|password|tokenValue)").matcher(src).find(),
                    "AR-PRV2-06 secret value field in " + p);
        }
        assertTrue(anyFileContains(RUNTIME, Pattern.compile("CredentialRef")),
                "AR-PRV2-06 CredentialRef missing");
    }

    // --- AR-PRV2-07: Durable media output uses ArtifactRef ---
    @org.junit.jupiter.api.Test
    void arPrv2_07_durableOutputUsesArtifactRef() throws IOException {
        Path result = RUNTIME.resolve("PluginExecutionResult.java");
        String src = stripComments(read(result));
        // GCR-2: shared-kernel ArtifactRef retired; durable outputs are typed
        // List<ArtifactId> (stable logical identity, no storage-URI semantics).
        assertTrue(src.contains("List<ArtifactId>"), "AR-PRV2-07 typed ArtifactId list missing");
        assertFalse(src.contains("ArtifactRef"), "AR-PRV2-07 legacy ArtifactRef must be absent");
        assertFalse(src.contains("InputStream"), "AR-PRV2-07 InputStream in result");
        assertFalse(src.contains("byte[]"), "AR-PRV2-07 byte[] in result");
    }

    // --- AR-PRV2-08: Provider SDK types do not cross public runtime API ---
    @org.junit.jupiter.api.Test
    void arPrv2_08_noSdkTypesInPublicApi() throws IOException {
        for (Path p : javaFiles(RUNTIME)) {
            if (p.toString().contains("/internal/")) {
                continue;
            }
            String src = read(p);
            assertFalse(Pattern.compile("openai|OpenAI|aws|AWS|okhttp|OkHttp").matcher(src).find(),
                    "AR-PRV2-08 SDK type in public API: " + p);
        }
    }

    // --- AR-PRV2-09: Every metered runtime execution has Usage boundary ---
    @org.junit.jupiter.api.Test
    void arPrv2_09_usageBoundaryExists() throws IOException {
        Path emitter = RUNTIME_INTERNAL.resolve("RuntimeUsageEmitter.java");
        assertTrue(Files.exists(emitter), "AR-PRV2-09 RuntimeUsageEmitter missing");
        assertTrue(read(emitter).contains("UsageRecordEmissionPort"),
                "AR-PRV2-09 must use UsageRecordEmissionPort");
    }

    // --- AR-PRV2-10: Provider cost preserves provenance ---
    @org.junit.jupiter.api.Test
    void arPrv2_10_costProvenancePreserved() throws IOException {
        // runtime never invents cost authorities; adapter preserves provider observations
        Path runtime = RUNTIME_INTERNAL.resolve("DefaultPluginRuntime.java");
        String src = read(runtime);
        assertTrue(src.contains("metrics"), "AR-PRV2-10 provider observations preserved");
    }

    // --- AR-PRV2-11: Retry ownership is explicit ---
    @org.junit.jupiter.api.Test
    void arPrv2_11_retryOwnershipExplicit() throws IOException {
        Path runtime = RUNTIME_INTERNAL.resolve("DefaultPluginRuntime.java");
        String src = read(runtime);
        assertTrue(src.contains("RETRY_OWNERSHIP") || src.contains("does NOT own retry"),
                "AR-PRV2-11 retry ownership must be documented");
    }

    // --- AR-PRV2-12: Timeout hierarchy is bounded ---
    @org.junit.jupiter.api.Test
    void arPrv2_12_timeoutBounded() throws IOException {
        Path req = RUNTIME.resolve("PluginExecutionRequest.java");
        String src = read(req);
        assertTrue(src.contains("MAX_TIMEOUT"), "AR-PRV2-12 MAX_TIMEOUT missing");
        assertTrue(src.contains("120"), "AR-PRV2-12 120s cap missing");
    }

    // --- AR-PRV2-13: Cancellation semantics are mode-specific ---
    @org.junit.jupiter.api.Test
    void arPrv2_13_cancellationModeSpecific() throws IOException {
        Path result = RUNTIME.resolve("PluginExecutionResult.java");
        assertTrue(read(result).contains("cancelled"), "AR-PRV2-13 cancelled factory missing");
        Path status = RUNTIME.resolve("PluginExecutionStatus.java");
        assertTrue(read(status).contains("CANCELLED"), "AR-PRV2-13 CANCELLED status missing");
    }

    // --- AR-PRV2-14: Runtime progress != domain lifecycle ---
    @org.junit.jupiter.api.Test
    void arPrv2_14_progressIsObservation() throws IOException {
        Path progress = RUNTIME.resolve("PluginExecutionProgress.java");
        String src = stripComments(read(progress));
        // Progress is an immutable record (observation type), not a mutable lifecycle aggregate
        assertTrue(src.contains("record PluginExecutionProgress"),
                "AR-PRV2-14 progress must be an immutable record");
        assertFalse(src.contains("RenderJob"), "AR-PRV2-14 progress must not touch domain lifecycle");
        assertFalse(src.contains("DeliveryJob"), "AR-PRV2-14 progress must not touch domain lifecycle");
    }

    // --- AR-PRV2-15: Runtime resource capacity != product quota ---
    @org.junit.jupiter.api.Test
    void arPrv2_15_capacityNotQuota() throws IOException {
        for (Path p : javaFiles(RUNTIME)) {
            String src = read(p);
            assertFalse(src.contains("QuotaService"), "AR-PRV2-15 quota usage in " + p);
            assertFalse(src.contains("EntitlementService"), "AR-PRV2-15 entitlement usage in " + p);
        }
    }

    // --- AR-PRV2-16: domain modules only depend on exposed runtime API ---
    @org.junit.jupiter.api.Test
    void arPrv2_16_domainsOnlyUseExposedRuntimeApi() throws IOException {
        // ai/render must not import extension.runtime.internal
        assertFalse(anyFileContains(AI, Pattern.compile("extension\\.runtime\\.internal")),
                "AR-PRV2-16 AI imports runtime internals");
        assertFalse(anyFileContains(RENDER, Pattern.compile("extension\\.runtime\\.internal")),
                "AR-PRV2-16 Render imports runtime internals");
    }

    // --- AR-PRV2-17: Runtime implementation does not depend on domain lifecycle services ---
    @org.junit.jupiter.api.Test
    void arPrv2_17_runtimeImplNoDomainLifecycle() throws IOException {
        Pattern domain = Pattern.compile(
                "render\\.app\\.RenderJobService|delivery\\.app\\.DeliveryJobService|social|workflow\\.app");
        assertFalse(anyFileContains(RUNTIME_INTERNAL, domain),
                "AR-PRV2-17 runtime impl depends on domain lifecycle");
    }

    // --- AR-PRV2-18: Plugin Runtime != Workflow Execution ---
    @org.junit.jupiter.api.Test
    void arPrv2_18_runtimeNotWorkflow() throws IOException {
        assertFalse(anyFileContains(RUNTIME, Pattern.compile("Temporal|WorkflowEngine|ActivityStub")),
                "AR-PRV2-18 runtime must not be workflow execution");
    }

    // --- AR-PRV2-19: Runtime object != Artifact ---
    @org.junit.jupiter.api.Test
    void arPrv2_19_runtimeObjectNotArtifact() throws IOException {
        for (Path p : javaFiles(RUNTIME)) {
            String src = read(p);
            assertFalse(Pattern.compile("(FFmpegArtifact|OpenCVArtifact|BMFArtifact|PluginArtifact)").matcher(src).find(),
                    "AR-PRV2-19 provider-specific artifact in " + p);
        }
    }

    // --- AR-PRV2-20: Cross-Modulith runtime changes require ModularityTest in Stage2 ---
    @org.junit.jupiter.api.Test
    void arPrv2_20_modulithSurfaceExposed() throws IOException {
        Path pkgInfo = RUNTIME.resolve("package-info.java");
        String src = read(pkgInfo);
        assertTrue(src.contains("NamedInterface"), "AR-PRV2-20 NamedInterface missing");
        assertTrue(src.contains("runtime"), "AR-PRV2-20 extension::runtime missing");
    }
}
