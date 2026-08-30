package com.example.platform.billing.app;

import com.example.platform.billing.domain.UsageMeter;
import com.example.platform.billing.infrastructure.BillableUsageJdbcRepository;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.BillableUsageAuditPort;
import com.example.platform.billing.usage.MeterUsageCommand;
import com.example.platform.billing.usage.MeteringRule;
import com.example.platform.billing.usage.MeteringRuleRegistry;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.shared.Ids;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billing-owned deterministic normalization from persisted observations to BillableUsage. */
@Service
public class UsageMeteringService {

    private final ObservedRuntimeUsageJdbcRepository observations;
    private final BillableUsageJdbcRepository billableUsage;
    private final MeteringRuleRegistry rules;
    private final BillableUsageAuditPort audit;
    private final Map<String, UsageMeter> meters = new ConcurrentHashMap<>();

    public UsageMeteringService(
            ObservedRuntimeUsageJdbcRepository observations,
            BillableUsageJdbcRepository billableUsage,
            MeteringRuleRegistry rules,
            BillableUsageAuditPort audit) {
        this.observations = observations;
        this.billableUsage = billableUsage;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional
    public BillableUsage meter(MeterUsageCommand command) {
        ObservedRuntimeUsage observation = observations.findByTenantAndId(
                command.tenantId(), command.observedUsageId()).orElseThrow(
                        () -> new IllegalStateException(
                                "Observed runtime usage was not durably persisted for tenant"));
        MeteringRule rule = rules.find(command.meteringRuleId(), command.meteringRuleVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown metering rule/version: " + command.meteringRuleId()
                                + "/" + command.meteringRuleVersion()));
        if (observation.dimension() != rule.sourceDimension()
                || observation.quantity().unit() != rule.sourceUnit()) {
            throw new IllegalStateException(
                    "Observation dimension/unit has no explicit mapping in metering rule/version");
        }

        long transformed = rule.transform(observation.quantity().baseUnits());
        BillableUsage candidate = BillableUsage.create(
                observation.tenantId(),
                observation.principalRef(),
                observation.observedUsageId(),
                observation.dimension(),
                observation.quantity(),
                rule,
                UsageQuantity.fromBaseUnits(transformed, rule.targetUnit()),
                observation.occurredAt(),
                command.meteredAt(),
                command.traceId(),
                "observedUsageId=" + observation.observedUsageId()
                        + ";source=" + observation.source()
                        + ";sourceReference=" + observation.sourceReference()
                        + ";observationTrace=" + observation.traceId());
        BillableUsageJdbcRepository.AppendResult result = billableUsage.appendResult(candidate);
        if (result.inserted()) {
            audit.recordMetered(result.usage());
        }
        return result.usage();
    }

    public List<BillableUsage> getBillableUsage(String tenantId, String meterKey) {
        return billableUsage.findByTenant(tenantId).stream()
                .filter(usage -> meterKey == null || meterKey.equals(usage.billableMeter()))
                .toList();
    }

    public List<BillableUsage> getBillableUsageByTenant(String tenantId) {
        return billableUsage.findByTenant(tenantId);
    }

    /** Read-only compatibility projection; it never accepts or creates legacy records. */
    public List<UsageRecord> getUsage(String tenantId, String meterKey) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required for usage reads");
        }
        return getBillableUsage(tenantId, meterKey).stream()
                .map(UsageRecord.class::cast)
                .toList();
    }

    /** Tenant-scoped read-only compatibility projection. */
    public List<UsageRecord> getUsageByTenant(String tenantId) {
        return getUsage(tenantId, null);
    }

    public UsageMeter registerMeter(
            String meterKey, String name, String description, String unit,
            String aggregationType) {
        UsageMeter meter = new UsageMeter(
                Ids.newId("mtr"), meterKey, name, description, unit, aggregationType, "ACTIVE");
        meters.put(meterKey, meter);
        return meter;
    }

    public UsageMeter getMeter(String meterKey) {
        return meters.get(meterKey);
    }

    public List<UsageMeter> getMeters() {
        return List.copyOf(meters.values());
    }
}
