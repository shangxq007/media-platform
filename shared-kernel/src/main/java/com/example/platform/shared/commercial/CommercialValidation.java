package com.example.platform.shared.commercial;

import java.util.List;

final class CommercialValidation {

    private CommercialValidation() {
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }

    static List<CommercialEvidenceRef> immutableEvidence(List<CommercialEvidenceRef> evidence) {
        return evidence == null ? List.of() : List.copyOf(evidence);
    }

    static void requireAllowedReasonConsistency(boolean allowed, CommercialDecisionReason reason) {
        if (allowed != (reason == CommercialDecisionReason.ALLOWED)) {
            throw new IllegalArgumentException("ALLOWED is required exactly for allowed decisions");
        }
    }
}
