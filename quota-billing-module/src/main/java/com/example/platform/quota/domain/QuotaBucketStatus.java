package com.example.platform.quota.domain;

public record QuotaBucketStatus(
        String bucketId,
        String tenantId,
        String featureCode,
        long limit,
        long currentUsage,
        double usageRatio,
        boolean exceeded,
        String period
) {
    public static QuotaBucketStatus from(QuotaBucket bucket) {
        return new QuotaBucketStatus(
                bucket.id(),
                bucket.tenantId(),
                bucket.featureCode(),
                bucket.limit(),
                bucket.currentUsage(),
                bucket.usageRatio(),
                bucket.isExceeded(),
                bucket.period()
        );
    }
}
