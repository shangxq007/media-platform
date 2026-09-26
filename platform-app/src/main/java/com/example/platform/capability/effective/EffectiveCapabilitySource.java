package com.example.platform.capability.effective;

/**
 * Fixed source order for the independent authorities composing the view.
 *
 * <p>Each constant keeps its source-authority identity (these names are pinned by the
 * billing/entitlement/payment convergence guard) and additionally publishes the canonical
 * cross-tier factor name via {@link #factorKey()}. That neutral vocabulary
 * ({@code capability, runtime, entitlement, quota, policy}) is the single naming contract
 * shared with the client projection ({@code frontend/src/foundation/effectiveAccess.tsx}
 * {@code EffectiveAccessFactors}); {@code EffectiveAccessFactorVocabularyConsistencyTest}
 * pins the two lists together.</p>
 */
public enum EffectiveCapabilitySource {
    CAPABILITY_LIFECYCLE("capability"),
    H1_RUNTIME_AVAILABILITY("runtime"),
    H5_ENTITLEMENT("entitlement"),
    H5_COMMERCIAL_QUOTA("quota"),
    ROLE_WORKSPACE_POLICY("policy");

    private final String factorKey;

    EffectiveCapabilitySource(String factorKey) {
        this.factorKey = factorKey;
    }

    /** Canonical factor name shared with the client effective-access vocabulary. */
    public String factorKey() {
        return factorKey;
    }
}
