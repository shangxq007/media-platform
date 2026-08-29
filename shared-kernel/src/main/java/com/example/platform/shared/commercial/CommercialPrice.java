package com.example.platform.shared.commercial;

import java.util.List;
import java.util.Objects;

/** Customer-facing commercial price output, distinct from technical execution cost. */
public record CommercialPrice(
        Money amount,
        String pricingAuthorityVersion,
        List<CommercialEvidenceRef> evidence) {

    public CommercialPrice {
        Objects.requireNonNull(amount, "amount must not be null");
        pricingAuthorityVersion = CommercialValidation.requireNonBlank(
                pricingAuthorityVersion, "pricingAuthorityVersion");
        evidence = CommercialValidation.immutableEvidence(evidence);
    }
}
