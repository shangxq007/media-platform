package com.example.platform.render.app;

import com.example.platform.entitlement.app.QuotaUsageAuthority;
import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaDecision;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderQuotaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private QuotaUsageAuthority authority;
    private RenderQuotaService service;

    @BeforeEach
    void setUp() {
        authority = mock(QuotaUsageAuthority.class);
        service = new RenderQuotaService(
                mock(ApplicationEventPublisher.class), authority,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completionDelegatesStableJobDerivedIdempotencyKeyExactlyOnce() {
        QuotaUsageResult expected = new QuotaUsageResult(
                "qop-1", tenantPrincipal("tenant-1"), "render",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"),
                1, 100, "render-job:job-42:render:completion",
                QuotaOperationKind.CONSUMPTION, QuotaUsageOutcome.APPLIED,
                0, 1, null, "render-job:job-42", "render completion job-42",
                NOW, NOW);
        when(authority.execute(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        QuotaUsageResult actual = service.consumeQuota("tenant-1", "job-42", "render", 1);

        assertSame(expected, actual);
        ArgumentCaptor<QuotaUsageCommand> command = ArgumentCaptor.forClass(QuotaUsageCommand.class);
        verify(authority, times(1)).execute(command.capture());
        assertEquals("render-job:job-42:render:completion", command.getValue().idempotencyKey());
        assertEquals("render-job:job-42", command.getValue().traceId());
        assertEquals(QuotaOperationKind.CONSUMPTION, command.getValue().operationKind());
    }

    @Test
    void precheckReturnsTypedCommercialQuotaDecision() throws Exception {
        QuotaDecision expected = new QuotaDecision(
                tenantPrincipal("tenant-1"), "render", 1, 100, 12,
                true, CommercialDecisionReason.ALLOWED, List.of(),
                "quota-usage-v1", "render-precheck:tenant-1:render", NOW);
        when(authority.decide(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        QuotaDecision actual = service.checkQuota("tenant-1", "render", 1);

        assertSame(expected, actual);
        Method method = RenderQuotaService.class.getMethod(
                "checkQuota", String.class, String.class, int.class);
        assertEquals(QuotaDecision.class, method.getReturnType());
        assertFalse(method.getReturnType().getName().contains("RuntimeEligibility"));
    }

    @Test
    void adapterHasNoRenderQuotaRepositoryDependency() {
        assertTrue(List.of(RenderQuotaService.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .noneMatch(type -> type.getSimpleName().equals("QuotaUsageRepository")));
        assertFalse(Files.exists(repositoryRoot().resolve(
                "render-module/src/main/java/com/example/platform/render/app/QuotaUsageRepository.java")));
    }

    private static PrincipalRef tenantPrincipal(String tenantId) {
        return PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("settings.gradle.kts"))) {
            return parent;
        }
        throw new IllegalStateException("Could not locate repository root from " + current);
    }
}
