package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/** Immutable durable commercial rating result with complete rule and usage provenance. */
public record RatedUsageRecord(
        String ratedUsageId, String tenantId, String billableUsageId,
        String pricingRuleId, long pricingRuleVersion, long quantityBaseUnits,
        Money amount, Map<String, String> ratingDetails, Instant ratedAt,
        String traceId, String idempotencyKey, String payloadFingerprint) {

    public RatedUsageRecord {
        ratingDetails = ratingDetails == null ? Map.of() : Map.copyOf(ratingDetails);
    }

    public long ratedAmountMinor() { return amount.amountMinor(); }
    public String currencyCode() { return amount.currency(); }
    public Instant createdAt() { return ratedAt; }

    public static String fingerprint(String tenantId, String billableUsageId,
                                     String pricingRuleId, long pricingRuleVersion,
                                     long quantityBaseUnits, Money amount,
                                     Instant ratedAt, String traceId) {
        String payload = String.join("\u001f", tenantId, billableUsageId, pricingRuleId,
                Long.toString(pricingRuleVersion), Long.toString(quantityBaseUnits),
                Long.toString(amount.amountMinor()), amount.currency(), ratedAt.toString(), traceId);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
