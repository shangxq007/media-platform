package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.policy.featureflag.FeatureFlagAuditService;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class AccessDecisionFeatureFlagServiceTest {

    private AccessDecisionFeatureFlagService service;
    private FeatureFlagService featureFlagService;
    private FeatureFlagAuditService auditService;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        auditService = mock(FeatureFlagAuditService.class);
        service = new AccessDecisionFeatureFlagService(featureFlagService, auditService);
    }

    @Test
    void evaluateForAccessDecisionWithEnabledFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", "ws-1", "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.getFlagsForContext(any(FeatureFlagContext.class)))
                .thenReturn(List.of(
                        new FeatureFlagDefinition("export.gpu.v2.enabled", "GPU Export", null,
                                FeatureFlagType.BOOLEAN, false, List.of(), List.of(),
                                true, "owner", List.of(), Instant.now(), Instant.now(), false)
                ));
        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.v2.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                service.evaluateForAccessDecision(request);

        assertNotNull(result);
        assertEquals(1, result.decisions().size());
        assertFalse(result.disabledByFlag());
        assertTrue(result.reasons().isEmpty());
        verify(auditService).auditEvaluated(any(FeatureFlagDecision.class), eq("user-1"));
    }

    @Test
    void evaluateForAccessDecisionWithDisabledFlag() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", "ws-1", "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.getFlagsForContext(any(FeatureFlagContext.class)))
                .thenReturn(List.of(
                        new FeatureFlagDefinition("export.gpu.v2.enabled", "GPU Export", null,
                                FeatureFlagType.BOOLEAN, false, List.of(), List.of(),
                                true, "owner", List.of(), Instant.now(), Instant.now(), false)
                ));
        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.v2.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                service.evaluateForAccessDecision(request);

        assertNotNull(result);
        assertTrue(result.disabledByFlag());
        assertFalse(result.reasons().isEmpty());
        assertTrue(result.reasons().get(0).contains("export.gpu.v2.enabled"));
    }

    @Test
    void evaluateForAccessDecisionSkipsArchivedFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.getFlagsForContext(any(FeatureFlagContext.class)))
                .thenReturn(List.of(
                        new FeatureFlagDefinition("archived.flag", "Archived", null,
                                FeatureFlagType.BOOLEAN, false, List.of(), List.of(),
                                true, "owner", List.of(), Instant.now(), Instant.now(), true)
                ));

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                service.evaluateForAccessDecision(request);

        assertNotNull(result);
        assertTrue(result.decisions().isEmpty());
        assertFalse(result.disabledByFlag());
        verify(featureFlagService, never()).evaluate(any(FeatureFlagEvaluationRequest.class));
    }

    @Test
    void evaluateForAccessDecisionSkipsDisabledFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.getFlagsForContext(any(FeatureFlagContext.class)))
                .thenReturn(List.of(
                        new FeatureFlagDefinition("disabled.flag", "Disabled", null,
                                FeatureFlagType.BOOLEAN, false, List.of(), List.of(),
                                false, "owner", List.of(), Instant.now(), Instant.now(), false)
                ));

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                service.evaluateForAccessDecision(request);

        assertNotNull(result);
        assertTrue(result.decisions().isEmpty());
        assertFalse(result.disabledByFlag());
    }

    @Test
    void evaluateSpecificFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", "ws-1", "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("flag1", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("flag2", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        List<FeatureFlagDecision> decisions = service.evaluateSpecificFlags(
                request, List.of("flag1", "flag2"));

        assertEquals(2, decisions.size());
        assertTrue(decisions.get(0).enabled());
        assertFalse(decisions.get(1).enabled());
        verify(auditService, times(2)).auditEvaluated(any(FeatureFlagDecision.class), eq("user-1"));
    }

    @Test
    void evaluateForAccessDecisionWithNoFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        when(featureFlagService.getFlagsForContext(any(FeatureFlagContext.class)))
                .thenReturn(List.of());

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                service.evaluateForAccessDecision(request);

        assertNotNull(result);
        assertTrue(result.decisions().isEmpty());
        assertFalse(result.disabledByFlag());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void featureFlagAccessResultRecord() {
        List<FeatureFlagDecision> decisions = List.of();
        List<String> reasons = List.of("reason1");
        AccessDecisionFeatureFlagService.FeatureFlagAccessResult result =
                new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                        decisions, true, reasons);
        assertEquals(decisions, result.decisions());
        assertTrue(result.disabledByFlag());
        assertEquals(reasons, result.reasons());
    }
}
