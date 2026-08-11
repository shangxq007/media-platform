package com.example.platform.billing.app;

import com.example.platform.billing.domain.UsageMeter;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Billing-owned usage metering facade.
 *
 * <p>Canonical usage authority lives in {@code billing.usage} ({@link UsageRecord} with typed
 * {@link UsageQuantity}). This service keeps an in-memory projection for the billing cycle and
 * dashboard consumers; it never invents usage facts and never defines a second usage shape.
 * The legacy {@code double} quantity is gone — the typed base-unit count is used throughout.</p>
 */
@Service
public class UsageMeteringService {

    private static final Logger log = LoggerFactory.getLogger(UsageMeteringService.class);

    private final ConcurrentHashMap<String, UsageMeter> meters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UsageRecord> usageRecords = new ConcurrentHashMap<>();

    public UsageRecord recordUsage(String tenantId, String meterKey, double quantity, String unit,
                                   Instant recordedAt, String idempotencyKey) {
        String effectiveIdempotencyKey = idempotencyKey != null
                ? idempotencyKey
                : "usg-" + java.util.UUID.randomUUID();
        Instant effectiveRecordedAt = recordedAt != null ? recordedAt : Instant.now();
        if (usageRecords.containsKey(effectiveIdempotencyKey)) {
            log.info("UsageMeteringService: duplicate usage record with idempotencyKey={}", effectiveIdempotencyKey);
            return usageRecords.get(effectiveIdempotencyKey);
        }

        UsageUnit canonicalUnit = unitOf(unit);
        UsageDimension dimension = dimensionOf(canonicalUnit);
        UsageRecord record = UsageRecord.record(
                tenantId,
                null,
                null,
                OperationRef.of("metering-" + java.util.UUID.randomUUID()),
                null,
                null,
                null,
                dimension,
                new UsageQuantity((long) Math.round(quantity), canonicalUnit),
                effectiveRecordedAt,
                effectiveRecordedAt,
                effectiveRecordedAt,
                effectiveIdempotencyKey,
                "REPORTED",
                "legacy-metering");
        usageRecords.put(record.recordId(), record);
        if (idempotencyKey != null) {
            usageRecords.put(effectiveIdempotencyKey, record);
        }
        log.info("UsageMeteringService: recorded usage {} dimension={} quantity={} {}",
                record.recordId(), dimension.name(), record.quantity().baseUnits(), unit);
        return record;
    }

    private static UsageUnit unitOf(String unit) {
        if (unit == null) {
            return UsageUnit.COUNT;
        }
        return switch (unit.toLowerCase()) {
            case "seconds", "milliseconds", "ms", "s" -> UsageUnit.SECONDS;
            case "bytes", "byte", "b" -> UsageUnit.BYTE;
            case "tokens", "token" -> UsageUnit.TOKEN;
            default -> UsageUnit.COUNT;
        };
    }

    private static UsageDimension dimensionOf(UsageUnit unit) {
        return switch (unit) {
            case SECONDS, MILLISECONDS -> UsageDimension.DURATION;
            case BYTE -> UsageDimension.BYTE_STORED;
            case TOKEN -> UsageDimension.TOKEN_INPUT;
            default -> UsageDimension.REQUEST;
        };
    }

    public UsageMeter registerMeter(String meterKey, String name, String description,
                                    String unit, String aggregationType) {
        String meterId = Ids.newId("mtr");
        UsageMeter meter = new UsageMeter(meterId, meterKey, name, description,
                unit, aggregationType, "ACTIVE");
        meters.put(meterKey, meter);
        log.info("UsageMeteringService: registered meter {}", meterKey);
        return meter;
    }

    public UsageMeter getMeter(String meterKey) {
        return meters.get(meterKey);
    }

    public List<UsageMeter> getMeters() {
        return List.copyOf(meters.values());
    }

    public List<UsageRecord> getUsage(String tenantId, String meterKey) {
        return usageRecords.values().stream()
                .filter(r -> tenantId == null || tenantId.equals(r.tenantId()))
                .filter(r -> meterKey == null || meterKey.equals(r.dimension().name()))
                .toList();
    }

    public List<UsageRecord> getUsageByTenant(String tenantId) {
        return usageRecords.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()))
                .toList();
    }

    public UsageRecord getUsageRecord(String recordId) {
        return usageRecords.get(recordId);
    }
}
