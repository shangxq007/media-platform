package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.shared.commercial.QuotaDecision;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sole logical writer and decision boundary for canonical quota usage. */
@Service
public class QuotaUsageAuthority {

    public static final String AUTHORITY_VERSION = "quota-usage-v1";

    private final QuotaUsageJdbcRepository repository;

    public QuotaUsageAuthority(QuotaUsageJdbcRepository repository) {
        this.repository = repository;
    }

    /** Consumption and immutable operation audit execute in one proxy-owned transaction. */
    @Transactional
    public QuotaUsageResult execute(QuotaUsageCommand command) {
        return repository.apply(command);
    }

    @Transactional(readOnly = true)
    public long currentUsage(QuotaUsageQuery query) {
        return repository.currentUsage(query);
    }

    @Transactional(readOnly = true)
    public QuotaDecision decide(QuotaUsageQuery query) {
        long used = repository.currentUsage(query);
        boolean allowed = used <= query.limitUnits()
                && query.requestedUnits() <= query.limitUnits() - used;
        String evidenceId = String.join(":",
                query.principal().tenantId(),
                query.principal().principalType().name(),
                query.principal().principalId(),
                query.quotaKey(),
                query.periodStart().toString(),
                query.periodEnd().toString());
        return new QuotaDecision(
                query.principal(),
                query.quotaKey(),
                query.requestedUnits(),
                query.limitUnits(),
                used,
                allowed,
                allowed ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                List.of(new CommercialEvidenceRef(
                        "QuotaUsageAuthority", "QUOTA_USAGE_PERIOD", evidenceId)),
                AUTHORITY_VERSION,
                query.traceId(),
                query.decidedAt());
    }
}
