package com.example.platform.billing.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accumulates cost usage for a tenant/user over a period.
 */
public class CostUsageAccumulator {

    private final String tenantId;
    private final ConcurrentHashMap<String, Double> usageByCategory = new ConcurrentHashMap<>();
    private volatile double totalEstimatedCost;
    private volatile double totalActualCost;
    private volatile long totalRenderSeconds;
    private volatile long totalCpuSeconds;
    private volatile long totalGpuSeconds;
    private volatile long totalStorageBytes;
    private volatile long totalEgressBytes;
    private volatile int totalThirdPartyCalls;
    private volatile int totalJobCount;

    public CostUsageAccumulator(String tenantId) {
        this.tenantId = tenantId;
    }

    public void addRecord(RenderCostRecord record) {
        totalEstimatedCost += record.estimatedCost();
        totalActualCost += record.actualCost();
        totalRenderSeconds += record.durationSeconds();
        totalCpuSeconds += record.cpuSeconds();
        totalGpuSeconds += record.gpuSeconds();
        totalStorageBytes += record.storageBytes();
        totalEgressBytes += record.egressBytes();
        totalThirdPartyCalls += record.thirdPartyCalls();
        totalJobCount++;
        usageByCategory.merge(record.providerKey(), record.actualCost(), Double::sum);
    }

    public void addReservation(CostReservation reservation) {
        totalEstimatedCost += reservation.reservedAmount();
    }

    public void releaseReservation(CostReservation reservation) {
        // Reservation released, no actual cost incurred
    }

    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public double getTotalActualCost() { return totalActualCost; }
    public long getTotalRenderSeconds() { return totalRenderSeconds; }
    public long getTotalCpuSeconds() { return totalCpuSeconds; }
    public long getTotalGpuSeconds() { return totalGpuSeconds; }
    public long getTotalStorageBytes() { return totalStorageBytes; }
    public long getTotalEgressBytes() { return totalEgressBytes; }
    public int getTotalThirdPartyCalls() { return totalThirdPartyCalls; }
    public int getTotalJobCount() { return totalJobCount; }
    public String getTenantId() { return tenantId; }
    public Map<String, Double> getUsageByCategory() { return Map.copyOf(usageByCategory); }

    public UsageSummary toSummary() {
        return new UsageSummary(tenantId, totalEstimatedCost, totalActualCost,
                totalRenderSeconds, totalCpuSeconds, totalGpuSeconds,
                totalStorageBytes, totalEgressBytes, totalThirdPartyCalls, totalJobCount);
    }

    public record UsageSummary(
            String tenantId,
            double totalEstimatedCost,
            double totalActualCost,
            long totalRenderSeconds,
            long totalCpuSeconds,
            long totalGpuSeconds,
            long totalStorageBytes,
            long totalEgressBytes,
            int totalThirdPartyCalls,
            int totalJobCount) {}
}
