package com.example.platform.capability.effective;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable neutral boundary carrying a decision already made by a source authority. */
public record AuthorityDecisionInput(
        EffectiveCapabilitySource source,
        String authorityName,
        String decisionTarget,
        AuthorityDecisionResult result,
        List<String> reasonCodes,
        String authorityVersion,
        String decisionReference,
        Instant decidedAt,
        List<AuthorityProvenance> provenance,
        boolean stale,
        PrincipalRef principal,
        QuotaDecisionDetails quotaDetails) {

    public AuthorityDecisionInput(
            EffectiveCapabilitySource source,
            String authorityName,
            String decisionTarget,
            AuthorityDecisionResult result,
            List<String> reasonCodes,
            String authorityVersion,
            String decisionReference,
            Instant decidedAt,
            List<AuthorityProvenance> provenance,
            boolean stale,
            QuotaDecisionDetails quotaDetails) {
        this(
                source,
                authorityName,
                decisionTarget,
                result,
                reasonCodes,
                authorityVersion,
                decisionReference,
                decidedAt,
                provenance,
                stale,
                null,
                quotaDetails);
    }

    public AuthorityDecisionInput {
        Objects.requireNonNull(source, "source must not be null");
        authorityName = EffectiveCapabilityValidation.requireNonBlank(authorityName, "authorityName");
        decisionTarget = EffectiveCapabilityValidation.requireNonBlank(decisionTarget, "decisionTarget");
        Objects.requireNonNull(result, "result must not be null");
        reasonCodes = EffectiveCapabilityValidation.immutableNonBlank(reasonCodes, "reasonCodes");
        authorityVersion = EffectiveCapabilityValidation.requireNonBlank(authorityVersion, "authorityVersion");
        decisionReference = EffectiveCapabilityValidation.requireNonBlank(decisionReference, "decisionReference");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
        if ((source == EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA) != (quotaDetails != null)) {
            throw new IllegalArgumentException("quotaDetails must exist exactly for the quota source");
        }
    }
}
