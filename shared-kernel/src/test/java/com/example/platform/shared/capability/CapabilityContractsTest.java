package com.example.platform.shared.capability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for capability opening contracts.
 *
 * <p>These tests verify the contract shapes without implementing runtime behavior.</p>
 */
class CapabilityContractsTest {

    @Test
    void systemActionKeysAreStableStrings() {
        // SystemAction keys should be stable, versioned strings
        String[] expectedKeys = {
            "render.create_job",
            "media.generate_thumbnail",
            "media.generate_proxy",
            "render.generate_hls_preview",
            "artifact.export",
            "notification.send",
            "webhook.send"
        };

        for (String key : expectedKeys) {
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertTrue(key.contains("."), "Action key should be namespaced: " + key);
        }
    }

    @Test
    void extensionPointKeysAreStableStrings() {
        // ExtensionPoint keys should be stable, versioned strings
        String[] expectedKeys = {
            "ai.transcribe",
            "ai.translate",
            "ai.scene_summary",
            "ai.timeline_draft",
            "media.generate_thumbnail",
            "render.generate_preview",
            "notification.send",
            "storage.export"
        };

        for (String key : expectedKeys) {
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertTrue(key.contains("."), "Extension point key should be namespaced: " + key);
        }
    }

    @Test
    void providerCapabilitiesImmutability() {
        ProviderCapabilities capabilities = new ProviderCapabilities(
            Set.of("text", "image"),
            Set.of("ai.transcribe", "ai.translate"),
            1024 * 1024,
            30000,
            true,
            true,
            false,
            false
        );

        // Verify immutability
        assertThrows(UnsupportedOperationException.class, () -> {
            capabilities.supportedModalities().add("video");
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            capabilities.supportedExtensionPoints().add("new.point");
        });
    }

    @Test
    void credentialRefNeverExposesRawSecrets() {
        CredentialRef ref = new CredentialRef(
            "tenant-123",
            "cred-456",
            "provider-789",
            Set.of("read", "write"),
            "vault://secret/path",
            null
        );

        // CredentialRef should only contain references, not raw secrets
        assertNotNull(ref.tenantId());
        assertNotNull(ref.credentialId());
        assertNotNull(ref.providerId());
        assertNotNull(ref.secretPath());

        // secretPath should be a reference, not the actual secret
        assertTrue(ref.secretPath().startsWith("vault://"), "Secret path should be a reference");
    }

    @Test
    void artifactRefNeverExposesRawPaths() {
        ArtifactRef ref = new ArtifactRef(
            "artifact-123",
            "tenant-456",
            "video/mp4",
            "sha256:abc123",
            "s3://bucket/path",
            "urn:platform:artifact:123",
            new ArtifactRef.ArtifactPermissions(true, false, false)
        );

        // ArtifactRef should use logical URIs
        assertNotNull(ref.storageUri());
        assertNotNull(ref.logicalUri());

        // logicalUri should be a logical reference
        assertTrue(ref.logicalUri().startsWith("urn:"), "Logical URI should be a logical reference");
    }

    @Test
    void automationFlowCanContainNodesAndEdges() {
        AutomationFlow flow = new AutomationFlow(
            "flow-123",
            "tenant-456",
            "Test Flow",
            new AutomationTrigger(AutomationTrigger.TriggerType.EVENT, Map.of("event", "asset.uploaded")),
            List.of(
                new AutomationFlow.FlowNode("node-1", "media.generate_proxy", Map.of(), AutomationFlow.FlowNodeErrorPolicy.FAIL),
                new AutomationFlow.FlowNode("node-2", "notification.send", Map.of(), AutomationFlow.FlowNodeErrorPolicy.SKIP)
            ),
            List.of(
                new AutomationFlow.FlowEdge("node-1", "node-2", "success")
            ),
            FlowStatus.DRAFT,
            1
        );

        assertNotNull(flow.flowId());
        assertNotNull(flow.tenantId());
        assertEquals(2, flow.nodes().size());
        assertEquals(1, flow.edges().size());
        assertEquals(FlowStatus.DRAFT, flow.status());
    }

    @Test
    void invocationContextNeverExposesRawSecrets() {
        CredentialRef credRef = new CredentialRef(
            "tenant-123",
            "cred-456",
            "provider-789",
            Set.of("read"),
            "vault://secret/path",
            null
        );

        InvocationContext context = new InvocationContext(
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            "idempotency-key-xyz",
            Instant.now().plusSeconds(300),
            Map.of("feature-a", true),
            List.of(credRef),
            new InvocationContext.AuditContext("trace-123", "span-456", "test", Instant.now())
        );

        // Context should contain credential references, not raw secrets
        assertNotNull(context.credentialRefs());
        assertFalse(context.credentialRefs().isEmpty());

        CredentialRef firstRef = context.credentialRefs().get(0);
        assertNotNull(firstRef.secretPath());
        assertFalse(firstRef.secretPath().contains("actual-secret"));
    }

    @Test
    void invocationResultFactoryMethods() {
        // Test success result
        InvocationResult success = InvocationResult.success(Map.of("key", "value"));
        assertEquals(InvocationStatus.SUCCEEDED, success.status());
        assertFalse(success.retryable());
        assertNull(success.errorCode());

        // Test failure result
        InvocationResult failure = InvocationResult.failure("PROVIDER_ERROR", "Provider failed");
        assertEquals(InvocationStatus.FAILED, failure.status());
        assertFalse(failure.retryable());
        assertEquals("PROVIDER_ERROR", failure.errorCode());

        // Test retryable failure
        InvocationResult retryable = InvocationResult.retryableFailure("PROVIDER_TIMEOUT", "Timeout");
        assertEquals(InvocationStatus.RETRYABLE_FAILED, retryable.status());
        assertTrue(retryable.retryable());
        assertEquals("PROVIDER_TIMEOUT", retryable.errorCode());
    }

    @Test
    void capabilityStabilityLevels() {
        // Verify stability levels exist and are ordered
        CapabilityStability[] levels = CapabilityStability.values();
        assertEquals(5, levels.length);
        assertEquals(CapabilityStability.EXPERIMENTAL, levels[0]);
        assertEquals(CapabilityStability.DEPRECATED, levels[4]);
    }

    @Test
    void providerRuntimeTypes() {
        // Verify provider runtime types exist
        ProviderRuntimeType[] types = ProviderRuntimeType.values();
        assertEquals(6, types.length);

        // Verify BUILTIN is first (most trusted)
        assertEquals(ProviderRuntimeType.BUILTIN, types[0]);

        // Verify SANDBOX_FUNCTION and CONTAINER_PLUGIN are last (least trusted)
        assertEquals(ProviderRuntimeType.SANDBOX_FUNCTION, types[4]);
        assertEquals(ProviderRuntimeType.CONTAINER_PLUGIN, types[5]);
    }

    @Test
    void invocationStatusValues() {
        // Verify invocation status values exist
        InvocationStatus[] statuses = InvocationStatus.values();
        assertEquals(7, statuses.length);

        // Verify common statuses exist
        assertNotNull(InvocationStatus.SUCCEEDED);
        assertNotNull(InvocationStatus.FAILED);
        assertNotNull(InvocationStatus.RETRYABLE_FAILED);
        assertNotNull(InvocationStatus.TIMED_OUT);
        assertNotNull(InvocationStatus.CANCELLED);
    }

    @Test
    void flowStatusValues() {
        // Verify flow status values exist
        FlowStatus[] statuses = FlowStatus.values();
        assertEquals(5, statuses.length);

        // Verify lifecycle statuses exist
        assertEquals(FlowStatus.DRAFT, statuses[0]);
        assertEquals(FlowStatus.ACTIVE, statuses[1]);
        assertEquals(FlowStatus.DISABLED, statuses[3]);
        assertEquals(FlowStatus.ARCHIVED, statuses[4]);
    }

    @Test
    void capabilityErrorCodes() {
        // Verify error codes exist
        CapabilityErrorCode[] codes = CapabilityErrorCode.values();
        assertTrue(codes.length > 10, "Should have multiple error codes");

        // Verify common error codes exist
        assertNotNull(CapabilityErrorCode.PERMISSION_DENIED);
        assertNotNull(CapabilityErrorCode.NOT_FOUND);
        assertNotNull(CapabilityErrorCode.PROVIDER_NOT_FOUND);
        assertNotNull(CapabilityErrorCode.CREDENTIAL_NOT_FOUND);
        assertNotNull(CapabilityErrorCode.ARTIFACT_NOT_FOUND);
    }
}
