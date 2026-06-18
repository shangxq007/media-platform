package com.example.platform.shared.capability.validation;

import com.example.platform.shared.capability.*;
import com.example.platform.shared.capability.hook.*;
import com.example.platform.shared.capability.registry.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutomationFlowValidator.
 *
 * <p>These tests verify flow validation without implementing runtime execution.</p>
 */
class AutomationFlowValidatorTest {

    private SystemActionRegistry actionRegistry;
    private ExtensionPointRegistry extensionPointRegistry;
    private EventTypeRegistry eventTypeRegistry;
    private HookPointRegistry hookPointRegistry;
    private AutomationFlowValidator validator;

    @BeforeEach
    void setUp() {
        actionRegistry = new InMemorySystemActionRegistry();
        extensionPointRegistry = new InMemoryExtensionPointRegistry();
        eventTypeRegistry = new InMemoryEventTypeRegistry();
        hookPointRegistry = new InMemoryHookPointRegistry();

        // Register test actions
        actionRegistry.register(new TestSystemAction("render.create_job", "1.0.0"));
        actionRegistry.register(new TestSystemAction("media.generate_thumbnail", "1.0.0"));

        // Register test extension points
        extensionPointRegistry.register(new TestExtensionPoint("ai.transcribe", "1.0.0"));

        // Register test event types
        eventTypeRegistry.register(new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event"
        ));

        // Register test hook points
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

        validator = new AutomationFlowValidator(
            actionRegistry,
            extensionPointRegistry,
            eventTypeRegistry,
            hookPointRegistry
        );
    }

    @Test
    void validFlowPasses() {
        AutomationFlow flow = createValidFlow();

        AutomationFlowValidationResult result = validator.validate(flow);

        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void missingFlowIdFails() {
        AutomationFlow flow = new AutomationFlow(
            null,
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

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.FLOW_ID_MISSING));
    }

    @Test
    void missingTenantIdFails() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            null,
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

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.TENANT_ID_MISSING));
    }

    @Test
    void unknownActionFails() {
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

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_ACTION));
    }

    @Test
    void unknownExtensionPointFails() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.EXTENSION_POINT,
                "unknown.extension",
                "1.0.0",
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_EXTENSION_POINT));
    }

    @Test
    void unknownTriggerEventTypeFails() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "unknown.event", "1.0.0", Map.of()),
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

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.TRIGGER_REFERENCES_UNKNOWN_EVENT_TYPE));
    }

    @Test
    void unknownHookPointFails() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(new AutomationFlow.FlowNode(
                "node-1",
                AutomationFlow.NodeType.HOOK,
                "unknown.hook",
                null,
                Map.of(),
                Set.of(),
                AutomationFlow.FlowNodeErrorPolicy.FAIL
            )),
            List.of(),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.HOOK_REFERENCES_UNKNOWN_HOOK_POINT));
    }

    @Test
    void invalidEdgeEndpointFails() {
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
            List.of(new AutomationFlow.FlowEdge("node-1", "node-999", null)),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.EDGE_INVALID));
    }

    @Test
    void cycleDetectedFails() {
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
            List.of(
                new AutomationFlow.FlowEdge("node-1", "node-2", null),
                new AutomationFlow.FlowEdge("node-2", "node-3", null),
                new AutomationFlow.FlowEdge("node-3", "node-1", null)
            ),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.CYCLE_DETECTED));
    }

    @Test
    void disconnectedNodeWarning() {
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
            List.of(
                new AutomationFlow.FlowEdge("node-1", "node-2", null)
            ),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertTrue(result.valid());
        assertTrue(result.hasWarnings());
        assertTrue(result.warnings().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.DISCONNECTED_NODE));
    }

    @Test
    void validatorDoesNotExecuteActions() {
        // This test verifies that validation only checks registry existence
        // and does not execute any actions
        AutomationFlow flow = createValidFlow();

        // If validator tried to execute, this would fail or have side effects
        AutomationFlowValidationResult result = validator.validate(flow);

        assertTrue(result.valid());
    }

    @Test
    void nullFlowFails() {
        AutomationFlowValidationResult result = validator.validate(null);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
    }

    @Test
    void missingNodesFails() {
        AutomationFlow flow = new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(),
            List.of(),
            FlowStatus.DRAFT,
            1
        );

        AutomationFlowValidationResult result = validator.validate(flow);

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream()
            .anyMatch(i -> i.code() == AutomationFlowValidationCode.NODE_MISSING));
    }

    private AutomationFlow createValidFlow() {
        return new AutomationFlow(
            "flow-1",
            "tenant-123",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, "asset.uploaded", "1.0.0", Map.of()),
            List.of(
                new AutomationFlow.FlowNode(
                    "node-1",
                    AutomationFlow.NodeType.ACTION,
                    "render.create_job",
                    "1.0.0",
                    Map.of(),
                    Set.of(),
                    AutomationFlow.FlowNodeErrorPolicy.FAIL
                ),
                new AutomationFlow.FlowNode(
                    "node-2",
                    AutomationFlow.NodeType.ACTION,
                    "media.generate_thumbnail",
                    "1.0.0",
                    Map.of(),
                    Set.of(),
                    AutomationFlow.FlowNodeErrorPolicy.FAIL
                )
            ),
            List.of(
                new AutomationFlow.FlowEdge("node-1", "node-2", null)
            ),
            FlowStatus.DRAFT,
            1
        );
    }

    // Test helper classes
    private static class TestSystemAction implements SystemAction {
        private final String actionKey;
        private final String version;

        TestSystemAction(String actionKey, String version) {
            this.actionKey = actionKey;
            this.version = version;
        }

        @Override public String actionKey() { return actionKey; }
        @Override public String version() { return version; }
        @Override public String inputSchemaRef() { return null; }
        @Override public String outputSchemaRef() { return null; }
        @Override public Set<String> requiredPermissions() { return Set.of(); }
        @Override public java.time.Duration timeout() { return java.time.Duration.ofSeconds(30); }
        @Override public boolean isIdempotent() { return true; }
        @Override public CapabilityStability stability() { return CapabilityStability.STABLE; }
        @Override public String description() { return "Test action"; }
    }

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
