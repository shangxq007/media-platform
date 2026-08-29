package com.example.platform.billing.usage;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable Billing-owned usage that is eligible for commercial rating. */
public record BillableUsage(
        String billableUsageId,
        String tenantId,
        CanonicalActorRef principalRef,
        String observedUsageId,
        UsageDimension observedDimension,
        UsageQuantity observedQuantity,
        String billableMeter,
        UsageDimension billableDimension,
        UsageQuantity billableQuantity,
        String meteringRuleId,
        String meteringRuleVersion,
        MeteringTransformationKind transformationKind,
        String transformationDetails,
        Instant sourceObservationTimestamp,
        Instant meteredAt,
        String idempotencyKey,
        String traceId,
        String provenanceReference) implements UsageRecord {

    public BillableUsage {
        billableUsageId = requireNonBlank(billableUsageId, "billableUsageId");
        tenantId = requireNonBlank(tenantId, "tenantId");
        Objects.requireNonNull(principalRef, "principalRef must not be null");
        observedUsageId = requireNonBlank(observedUsageId, "observedUsageId");
        Objects.requireNonNull(observedDimension, "observedDimension must not be null");
        Objects.requireNonNull(observedQuantity, "observedQuantity must not be null");
        UsageUnit.validate(observedDimension, observedQuantity.unit());
        billableMeter = requireNonBlank(billableMeter, "billableMeter");
        Objects.requireNonNull(billableDimension, "billableDimension must not be null");
        Objects.requireNonNull(billableQuantity, "billableQuantity must not be null");
        UsageUnit.validate(billableDimension, billableQuantity.unit());
        meteringRuleId = requireNonBlank(meteringRuleId, "meteringRuleId");
        meteringRuleVersion = requireNonBlank(meteringRuleVersion, "meteringRuleVersion");
        Objects.requireNonNull(transformationKind, "transformationKind must not be null");
        transformationDetails = requireNonBlank(transformationDetails, "transformationDetails");
        Objects.requireNonNull(sourceObservationTimestamp,
                "sourceObservationTimestamp must not be null");
        Objects.requireNonNull(meteredAt, "meteredAt must not be null");
        idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        traceId = requireNonBlank(traceId, "traceId");
        provenanceReference = requireNonBlank(provenanceReference, "provenanceReference");
    }

    public static BillableUsage create(
            String tenantId,
            CanonicalActorRef principalRef,
            String observedUsageId,
            UsageDimension observedDimension,
            UsageQuantity observedQuantity,
            MeteringRule rule,
            UsageQuantity billableQuantity,
            Instant sourceObservationTimestamp,
            Instant meteredAt,
            String traceId,
            String provenanceReference) {
        String digest = deterministicDigest(tenantId, observedUsageId, rule.ruleId(), rule.version());
        return new BillableUsage(
                "bus-" + digest.substring(0, 60), tenantId, principalRef, observedUsageId,
                observedDimension, observedQuantity, rule.billableMeter(),
                rule.targetDimension(), billableQuantity, rule.ruleId(), rule.version(),
                rule.transformationKind(), rule.transformationDetails(),
                sourceObservationTimestamp, meteredAt,
                "billable-" + digest,
                traceId, provenanceReference);
    }

    public static String deterministicIdempotencyKey(
            String tenantId, String observedUsageId, String ruleId, String version) {
        return "billable-" + deterministicDigest(tenantId, observedUsageId, ruleId, version);
    }

    private static String deterministicDigest(
            String tenantId, String observedUsageId, String ruleId, String version) {
        String source = String.join("\u001f", tenantId, observedUsageId, ruleId, version);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @Override
    public String recordId() {
        return billableUsageId;
    }

    @Override
    public UsageDimension dimension() {
        return billableDimension;
    }

    @Override
    public UsageQuantity quantity() {
        return billableQuantity;
    }

    @Override
    public Instant recordedAt() {
        return meteredAt;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
