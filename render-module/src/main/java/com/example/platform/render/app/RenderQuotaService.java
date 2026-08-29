package com.example.platform.render.app;

import com.example.platform.entitlement.app.QuotaUsageAuthority;
import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaDecision;
import com.example.platform.shared.events.QuotaCheckRequestedEvent;
import com.example.platform.shared.events.QuotaCheckResultEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class RenderQuotaService {

    private final ApplicationEventPublisher eventPublisher;
    private final QuotaUsageAuthority quotaUsageAuthority;
    private final Clock clock;
    private static final long DEFAULT_LIMIT = 100;

    @Autowired
    public RenderQuotaService(
            ApplicationEventPublisher eventPublisher, QuotaUsageAuthority quotaUsageAuthority) {
        this(eventPublisher, quotaUsageAuthority, Clock.systemUTC());
    }

    RenderQuotaService(
            ApplicationEventPublisher eventPublisher,
            QuotaUsageAuthority quotaUsageAuthority,
            Clock clock) {
        this.eventPublisher = eventPublisher;
        this.quotaUsageAuthority = quotaUsageAuthority;
        this.clock = clock;
    }

    public QuotaDecision checkQuota(String tenantId, String featureCode, int requestedAmount) {
        eventPublisher.publishEvent(new QuotaCheckRequestedEvent(tenantId, featureCode, requestedAmount));
        Instant now = clock.instant();
        Period period = period(now);
        QuotaDecision decision = quotaUsageAuthority.decide(new QuotaUsageQuery(
                tenantPrincipal(tenantId), featureCode, period.start(), period.end(),
                requestedAmount, DEFAULT_LIMIT,
                "render-precheck:" + tenantId + ":" + featureCode, now));
        long remaining = Math.max(0, decision.limitUnits() - decision.usedUnits());
        eventPublisher.publishEvent(new QuotaCheckResultEvent(
                tenantId, featureCode, requestedAmount, decision.allowed(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, remaining))));
        return decision;
    }

    public QuotaUsageResult consumeQuota(
            String tenantId, String jobId, String featureCode, int amount) {
        Instant now = clock.instant();
        Period period = period(now);
        return quotaUsageAuthority.execute(new QuotaUsageCommand(
                tenantPrincipal(tenantId),
                featureCode,
                period.start(),
                period.end(),
                amount,
                DEFAULT_LIMIT,
                "render-job:" + jobId + ":" + featureCode + ":completion",
                QuotaOperationKind.CONSUMPTION,
                "render-job:" + jobId,
                "render completion " + jobId,
                now));
    }

    private static PrincipalRef tenantPrincipal(String tenantId) {
        // The compatibility adapter historically receives only a tenant. Until a
        // caller supplies a more specific principal, map it explicitly to the
        // organization principal kind instead of performing a subject-only write.
        return PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId);
    }

    private static Period period(Instant instant) {
        YearMonth month = YearMonth.from(instant.atZone(ZoneOffset.UTC));
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Period(start, end);
    }

    private record Period(Instant start, Instant end) {}
}
