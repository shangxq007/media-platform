package com.example.platform.shared.capability.flow;

import com.example.platform.shared.capability.*;
import com.example.platform.shared.capability.action.BuiltInSystemActions;
import com.example.platform.shared.capability.execution.*;
import com.example.platform.shared.capability.hook.*;
import com.example.platform.shared.capability.registry.*;
import com.example.platform.shared.capability.validation.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutomationFlowDryRunExecutor.
 *
 * <p>These tests verify dry-run execution without implementing real action execution.</p>
 */
class AutomationFlowDryRunExecutorTest {

    private SystemActionRegistry actionRegistry;
    private ExtensionPointRegistry extensionPointRegistry;
    private EventTypeRegistry eventTypeRegistry;
    private HookPointRegistry hookPointRegistry;
    private AutomationFlowValidator flowValidator;
    private SystemActionExecutor actionExecutor;
    private AutomationFlowDryRunExecutor dryRunExecutor;

    @BeforeEach
    void setUp() {
        actionRegistry = new InMemorySystemActionRegistry();
        extensionPointRegistry = new InMemoryExtensionPointRegistry();
        eventTypeRegistry = new InMemoryEventTypeRegistry();
        hookPointRegistry = new InMemoryHookPointRegistry();

        // Register built-in actions
        BuiltInSystemActions.registerInto(actionRegistry);

        // Register test event type
        eventTypeRegistry.register(new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event"
        ));

        // Register test extension point
        extensionPointRegistry.register(new TestExtensionPoint("ai.transcribe", "1.0.0"));

        // Register test hook point
        hookPointRegistry.register(new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            java.time.Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        ));

        flowValidator = new AutomationFlowValidator(
            actionRegistry,
            extensionPointRegistry,
            eventTypeRegistry,
            hookPointRegistry
        );

        actionExecutor = new ValidatingSystemActionExecutor(actionRegistry);

        dryRunExecutor = new AutomationFlowDryRunExecutor(
            flowValidator,
            actionExecutor,
            actionRegistry
        );
    }

    @Test
    void validFlowDryRunSucceeds() {
        AutomationFlow flow = createValidFlow();
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertTrue(result.dryRun());
        assertFalse(result.nodeResults().isEmpty());
    }

    @Test
    void validationErrorPreventsNodeProcessing() {
        // Create a flow with unknown action
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.ACTION,
                "unknown.action",
                "1.0.0",
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.VALIDATION_FAILED, result.status());
        assertTrue(result.nodeResults().isEmpty());
    }

    @Test
    void validationWarningStillAllowsDryRun() {
        // Create a flow with disconnected node (warning, not error)
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(
                new AutomationFlow.FlowNode("node-1", AutomationFlow.NodeType.ACTION, "render.create_job", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-2", AutomationFlow.NodeType.ACTION, "media.generate_thumbnail", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-3", AutomationFlow.NodeType.ACTION, "render.create_job", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL)
            ),
            List.of(new AutomationFlow.FlowEdge("node-1", "node-2", null)),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        // Should succeed with warnings
        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertTrue(result.validationResult().hasWarnings());
        assertFalse(result.nodeResults().isEmpty());
    }

    @Test
    void actionNodeDryRunsThroughSystemActionExecutor() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.ACTION,
                "render.create_job",
                "1.0.0",
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.DRY_RUN_SUCCEEDED, result.nodeResults().get(0).status());
    }

    @Test
    void extensionPointNodeIsNotImplemented() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.EXTENSION_POINT,
                "ai.transcribe",
                "1.0.0",
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.PARTIALLY_SUPPORTED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.NOT_IMPLEMENTED, result.nodeResults().get(0).status());
    }

    @Test
    void hookNodeIsNotImplemented() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.HOOK,
                "render.before_create",
                null,
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.PARTIALLY_SUPPORTED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.NOT_IMPLEMENTED, result.nodeResults().get(0).status());
    }

    @Test
    void notificationNodeMapsToSendActionIfRegistered() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.NOTIFICATION,
                "notification.send",
                null,
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.DRY_RUN_SUCCEEDED, result.nodeResults().get(0).status());
    }

    @Test
    void webhookNodeMapsToSendActionIfRegistered() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.WEBHOOK,
                "webhook.send",
                null,
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.DRY_RUN_SUCCEEDED, result.nodeResults().get(0).status());
    }

    @Test
    void conditionNodeIsSkipped() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.CONDITION,
                "condition.check",
                null,
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(AutomationFlowDryRunStatus.SUCCEEDED, result.status());
        assertEquals(1, result.nodeResults().size());
        assertEquals(AutomationNodeDryRunStatus.SKIPPED, result.nodeResults().get(0).status());
    }

    @Test
    void dryRunProducesDeterministicNodeResultOrder() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(
                new AutomationFlow.FlowNode("node-1", AutomationFlow.NodeType.ACTION, "render.create_job", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-2", AutomationFlow.NodeType.ACTION, "media.generate_thumbnail", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-3", AutomationFlow.NodeType.CONDITION, "condition.check", null, Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL)
            ),
            List.of(),
            FlowStatus.DRAFT,
            1
        );
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        assertEquals(3, result.nodeResults().size());
        assertEquals("node-1", result.nodeResults().get(0).nodeId());
        assertEquals("node-2", result.nodeResults().get(1).nodeId());
        assertEquals("node-3", result.nodeResults().get(2).nodeId());
    }

    @Test
    void noRealActionExecutionOccurs() {
        // This test verifies that dry-run does not execute real actions
        AutomationFlow flow = createValidFlow();
        AutomationFlowDryRunRequest request = createRequest(flow);

        AutomationFlowDryRunResult result = dryRunExecutor.execute(request);

        // All nodes should succeed in dry-run mode
        assertTrue(result.allNodesSucceeded());
        assertTrue(result.dryRun());
    }

    private AutomationFlow createValidFlow() {
        return new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(
                new AutomationFlow.FlowNode("node-1", AutomationFlow.NodeType.ACTION, "render.create_job", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-2", AutomationFlow.NodeType.ACTION, "media.generate_thumbnail", "1.0.0", Map.of(), Set.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL)
            ),
            List.of(new AutomationFlow.FlowEdge("node-1", "node-2", null)),
            FlowStatus.DRAFT,
            1
        );
    }

    private AutomationFlowDryRunRequest createRequest(AutomationFlow flow) {
        SystemActionExecutionContext context = new SystemActionExecutionContext(
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            "idempotency-key-1",
            Instant.now().plusSeconds(300),
            true, // dryRun
            Map.of(),
            List.of(),
            List.of()
        );

        return new AutomationFlowDryRunRequest(
            flow,
            context,
            Map.of("input", "test"),
            Map.of()
        );
    }

    // Test helper class
    private static class TestExtensionPoint implements ExtensionPoint {
        private final String key;
        private final String version;

        TestExtensionPoint(String key, String version) {
            this.key = key;
            this.version = version;
        }

        @Override public String key() { return key; }
        @Override public String version() { return version; }
        @Override public String inputSchemaRef() { return null; }
        @Override public String outputSchemaRef() { return null; }
        @Override public Set<String> requiredPermissions() { return Set.of(); }
        @Override public CapabilityStability stability() { return CapabilityStability.STABLE; }
        @Override public java.time.Duration timeout() { return java.time.Duration.ofSeconds(30); }
        @Override public Set<ProviderRuntimeType> allowedProviderTypes() { return Set.of(); }
        @Override public String description() { return "Test extension point"; }
    }
}
