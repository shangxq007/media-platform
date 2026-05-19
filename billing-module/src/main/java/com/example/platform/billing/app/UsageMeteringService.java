package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UsageMeteringService {

    private static final Logger log = LoggerFactory.getLogger(UsageMeteringService.class);

    private final ConcurrentHashMap<String, UsageMeter> meters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UsageRecord> usageRecords = new ConcurrentHashMap<>();

    public UsageRecord recordUsage(String tenantId, String workspaceId, String userId,
                                    String meterKey, double quantity, String unit,
                                    Instant recordedAt, String idempotencyKey) {
        if (idempotencyKey != null && usageRecords.containsKey(idempotencyKey)) {
            log.info("UsageMeteringService: duplicate usage record with idempotencyKey={}", idempotencyKey);
            return usageRecords.get(idempotencyKey);
        }

        String recordId = Ids.newId("usg");
        UsageRecord record = new UsageRecord(
                recordId, tenantId, workspaceId, userId,
                meterKey, quantity, unit, recordedAt, idempotencyKey);
        usageRecords.put(recordId, record);
        if (idempotencyKey != null) {
            usageRecords.put(idempotencyKey, record);
        }
        log.info("UsageMeteringService: recorded usage {} meter={} quantity={} {}",
                recordId, meterKey, quantity, unit);
        return record;
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
                .filter(r -> meterKey == null || meterKey.equals(r.meterKey()))
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
