package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.shared.commercial.QuotaConsumptionPort;
import com.example.platform.shared.commercial.QuotaConsumptionRequest;
import com.example.platform.shared.commercial.QuotaDecision;
import java.util.List;
import org.springframework.stereotype.Service;

/** Thin neutral adapter to the sole transactional {@link QuotaUsageAuthority}. */
@Service
public class QuotaConsumptionApplicationService implements QuotaConsumptionPort {
    private static final String AUTHORITY_VERSION = "quota-usage-v1";

    private final QuotaPolicyService policies;
    private final QuotaUsageAuthority authority;

    public QuotaConsumptionApplicationService(
            QuotaPolicyService policies, QuotaUsageAuthority authority) {
        this.policies = policies;
        this.authority = authority;
    }

    @Override
    public QuotaDecision consume(QuotaConsumptionRequest request) {
        long limit = policies.getQuotaPolicy(request.quotaKey()).limitValue();
        QuotaUsageResult result = authority.execute(new QuotaUsageCommand(
                request.principal(), request.quotaKey(), request.periodStart(), request.periodEnd(),
                request.amount(), limit, request.idempotencyKey(), QuotaOperationKind.CONSUMPTION,
                request.traceId(), request.reason(), request.occurredAt()));
        return new QuotaDecision(
                result.principal(), result.quotaKey(), request.amount(), result.limitValue(),
                result.usageAfter(), result.applied(),
                result.applied() ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                List.of(new CommercialEvidenceRef("QuotaUsageAuthority", "OPERATION", result.operationId())),
                AUTHORITY_VERSION, result.traceId(), result.recordedAt());
    }
}
