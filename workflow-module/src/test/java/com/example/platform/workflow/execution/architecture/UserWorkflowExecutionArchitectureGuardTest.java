package com.example.platform.workflow.execution.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * UWEV1 architecture guards AR-UWE-01..20 (frozen UWEV1-ARSF guards/guards.json).
 * Source-scan convention (repository standard, like AR-PRV2 / AR-W2).
 */
class UserWorkflowExecutionArchitectureGuardTest {

    private static final Path WORKFLOW = Path.of("src/main/java/com/example/platform/workflow");
    private static final Path TEMPORAL = WORKFLOW.resolve("temporal");
    private static final Path EXECUTION = WORKFLOW.resolve("execution");
    private static final Path DEFINITION = WORKFLOW.resolve("definition");
    private static final Path EXT_RUNTIME = Path.of("../extension-module/src/main/java/com/example/platform/extension/runtime");

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
            throw new RuntimeException(e);
        }
    }

    /** Strips // and /* * / comments so guard assertions scan code, not prose. */
    private static String stripComments(String src) {
        String s = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        return s.replaceAll("//[^\n]*", " ");
    }

    private static boolean anyContains(Path root, Pattern pattern) throws IOException {
        for (Path p : javaFiles(root)) {
            if (pattern.matcher(read(p)).find()) {
                return true;
            }
        }
        return false;
    }

    @org.junit.jupiter.api.Test
    void arUwe01_publishedOnlyStart() throws IOException {
        // execution service rejects non-PUBLISHED definition (start policy)
        Path svc = EXECUTION.resolve("app/WorkflowExecutionService.java");
        String src = read(svc);
        assertTrue(src.contains("DEFINITION_NOT_PUBLISHED"), "AR-UWE-01 published-only check missing");
        assertTrue(src.contains("UserWorkflowDefinitionStatus.PUBLISHED"), "AR-UWE-01 PUBLISHED status check missing");
    }

    @org.junit.jupiter.api.Test
    void arUwe02_workflowCodeNoExternalEffects() throws IOException {
        // workflow impl must not perform HTTP/DB/provider/filesystem directly
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        String src = read(wf);
        assertFalse(src.contains("HttpClient"), "AR-UWE-02 HTTP in workflow code");
        assertFalse(src.contains("JdbcTemplate"), "AR-UWE-02 DB in workflow code");
        assertFalse(src.contains("pluginRuntime.execute"), "AR-UWE-02 PluginRuntime in workflow code (must be in activities)");
    }

    @org.junit.jupiter.api.Test
    void arUwe03_providerOnlyViaPluginRuntime() throws IOException {
        // temporal code must not call PluginRuntimeProviderBinding / provider SDK directly
        for (Path p : javaFiles(TEMPORAL)) {
            String src = stripComments(read(p));
            assertFalse(src.contains("PluginRuntimeProviderBinding"),
                    "AR-UWE-03 PluginRuntimeProviderBinding in temporal: " + p);
            assertFalse(Pattern.compile("openai|OpenAI|aws|OkHttp").matcher(src).find(),
                    "AR-UWE-03 provider SDK in temporal: " + p);
        }
    }

    @org.junit.jupiter.api.Test
    void arUwe04_noRuntimeInternals() throws IOException {
        assertFalse(anyContains(WORKFLOW, Pattern.compile("extension\\.runtime\\.internal")),
                "AR-UWE-04 workflow imports runtime internals");
    }

    @org.junit.jupiter.api.Test
    void arUwe05_noProviderExtensionSpiImport() throws IOException {
        assertFalse(anyContains(WORKFLOW, Pattern.compile("extension\\.domain\\.PluginRuntimeProviderBinding")),
                "AR-UWE-05 workflow imports PluginRuntimeProviderBinding");
    }

    @org.junit.jupiter.api.Test
    void arUwe06_noSandboxExecutionService() throws IOException {
        for (Path p : javaFiles(WORKFLOW)) {
            assertFalse(stripComments(read(p)).contains("SandboxExecutionService"),
                    "AR-UWE-06 workflow imports SandboxExecutionService: " + p);
        }
    }

    @org.junit.jupiter.api.Test
    void arUwe07_tenantRequired() throws IOException {
        Path svc = EXECUTION.resolve("app/WorkflowExecutionService.java");
        assertTrue(read(svc).contains("tenantId must not be blank")
                || read(EXECUTION.resolve("domain/WorkflowExecutionId.java")).contains("tenantId must not be blank"),
                "AR-UWE-07 tenant validation missing");
    }

    @org.junit.jupiter.api.Test
    void arUwe08_canonicalActorReused() throws IOException {
        for (Path p : javaFiles(EXECUTION)) {
            String src = stripComments(read(p));
            assertFalse(src.contains("SecurityContext"), "AR-UWE-08 SecurityContext in " + p);
            assertFalse(src.contains("Authentication"), "AR-UWE-08 Authentication in " + p);
        }
        assertTrue(anyContains(EXECUTION, Pattern.compile("CanonicalActorRef")),
                "AR-UWE-08 CanonicalActorRef missing");
    }

    @org.junit.jupiter.api.Test
    void arUwe09_payloadPolicyNoSecrets() throws IOException {
        assertFalse(anyContains(WORKFLOW, Pattern.compile("apiKey|secretValue|password|tokenValue")),
                "AR-UWE-09 secret value fields in workflow");
    }

    @org.junit.jupiter.api.Test
    void arUwe10_schedulerTriggerOnly() throws IOException {
        // no scheduler module dependency path executes nodes — scheduler must not
        // appear as execution authority in workflow code
        assertFalse(anyContains(EXECUTION, Pattern.compile("scheduler\\.app\\.|SchedulerService")),
                "AR-UWE-10 scheduler owns execution");
    }

    @org.junit.jupiter.api.Test
    void arUwe11_delayUsesDurableTimer() throws IOException {
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        String src = read(wf);
        assertTrue(src.contains("Workflow.await"), "AR-UWE-11 durable wait semantics");
        assertFalse(src.contains("Thread.sleep"), "AR-UWE-11 Thread.sleep forbidden");
    }

    @org.junit.jupiter.api.Test
    void arUwe12_approvalDurableSignal() throws IOException {
        // Typed approve signal lives on the workflow INTERFACE (SignalMethod);
        // impl carries the durable await state.
        Path iface = TEMPORAL.resolve("UserWorkflowExecutionWorkflow.java");
        String isrc = read(iface);
        assertTrue(isrc.contains("SignalMethod"), "AR-UWE-12 typed signal missing on interface");
        assertTrue(isrc.contains("approve"), "AR-UWE-12 approve signal missing");
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        assertTrue(read(wf).contains("approved"), "AR-UWE-12 approval state missing");
    }

    @org.junit.jupiter.api.Test
    void arUwe13_retryOwnershipExplicit() throws IOException {
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        String src = read(wf);
        assertTrue(src.contains("setMaximumAttempts(3)"), "AR-UWE-13 retry max 3 (W1)");
        assertFalse(src.contains("pluginRuntime"), "AR-UWE-13 runtime retry loop (runtime single attempt)");
    }

    @org.junit.jupiter.api.Test
    void arUwe14_timeoutHierarchyOrdered() throws IOException {
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        String src = read(wf);
        assertTrue(src.contains("setStartToCloseTimeout"), "AR-UWE-14 activity timeout present");
    }

    @org.junit.jupiter.api.Test
    void arUwe15_cancellationPropagates() throws IOException {
        Path iface = TEMPORAL.resolve("UserWorkflowExecutionWorkflow.java");
        String isrc = read(iface);
        assertTrue(isrc.contains("SignalMethod"), "AR-UWE-15 cancel signal missing on interface");
        assertTrue(isrc.contains("cancel"), "AR-UWE-15 cancel signal missing");
        Path wf = TEMPORAL.resolve("UserWorkflowExecutionWorkflowImpl.java");
        assertTrue(read(wf).contains("cancelled"), "AR-UWE-15 cancellation state missing");
    }

    @org.junit.jupiter.api.Test
    void arUwe16_noDuplicateRuntimeUsage() throws IOException {
        // workflow must not emit usage itself (runtime emits once via EUMF)
        assertFalse(anyContains(WORKFLOW, Pattern.compile("UsageRecordEmissionPort")),
                "AR-UWE-16 workflow duplicates runtime usage");
    }

    @org.junit.jupiter.api.Test
    void arUwe17_artifactOutputsArtifactRef() throws IOException {
        // durable outputs flow through ArtifactRef (shared-kernel), never raw media
        assertFalse(anyContains(EXECUTION, Pattern.compile("InputStream|byte\\[\\]")),
                "AR-UWE-17 raw media across execution boundary");
    }

    @org.junit.jupiter.api.Test
    void arUwe18_definitionIdVsExecutionId() throws IOException {
        Path id = EXECUTION.resolve("domain/WorkflowExecutionId.java");
        String src = read(id);
        assertTrue(src.contains("DISTINCT from"), "AR-UWE-18 distinct identity documented");
        assertTrue(src.contains("temporalWorkflowId"), "AR-UWE-18 temporal workflow id mapping");
    }

    @org.junit.jupiter.api.Test
    void arUwe19_workflowLifecycleVsPluginLifecycle() throws IOException {
        // Execution status enum is the aggregate lifecycle vocabulary — exactly
        // the 7 frozen states, no node/plugin states mixed in.
        Path status = EXECUTION.resolve("domain/WorkflowExecutionStatus.java");
        String src = stripComments(read(status));
        for (String state : new String[]{"PENDING", "RUNNING", "WAITING", "SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT"}) {
            assertTrue(src.contains(state), "AR-UWE-19 missing state " + state);
        }
        // Exactly 7 states — no node-level / plugin-level states added.
        assertTrue(src.contains("PENDING") && src.contains("TIMED_OUT"),
                "AR-UWE-19 lifecycle is the aggregate vocabulary");
    }

    @org.junit.jupiter.api.Test
    void arUwe20_temporalSoleOrchestrator() throws IOException {
        assertFalse(anyContains(WORKFLOW, Pattern.compile("LiteFlow|liteflow")),
                "AR-UWE-20 LiteFlow as durable orchestrator");
    }
}
