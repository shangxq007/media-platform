package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.runtime.CredentialRef;

import java.util.Objects;
import java.util.Set;

/**
 * Secret resolution boundary (PLUGIN_RUNTIME_SECRET_INJECTION_POLICY_V1).
 *
 * <p>Canonical requests carry {@link CredentialRef} references ONLY. Values are
 * resolved at the execution boundary (last responsible moment), bound to
 * tenant + provider/capability context. Resolved values must NEVER enter outbox,
 * UsageRecord, Artifact provenance, logs or exception messages (AR-PRV2-06,
 * PRV2-RED-005).</p>
 */
public interface SecretRefResolver {

    /**
     * Resolves a credential reference to its value at the execution boundary.
     *
     * @param ref credential reference (never null)
     * @return resolved secret value, or {@code null} when unresolvable
     */
    String resolve(CredentialRef ref);

    /**
     * No-op resolver: resolves nothing (foundation default when no secret store
     * is wired). Guarantees no secret value can enter the canonical boundary.
     */
    SecretRefResolver NOOP = new SecretRefResolver() {
        @Override
        public String resolve(CredentialRef ref) {
            return null;
        }
    };

    /**
     * Static assertion helper used by guards/tests: the request carries only
     * references, never values.
     */
    static void assertNoSecretValues(Set<CredentialRef> secretRefs) {
        if (secretRefs == null) {
            return;
        }
        for (CredentialRef ref : secretRefs) {
            Objects.requireNonNull(ref, "CredentialRef must not be null");
            if (ref.secretRef() == null || ref.secretRef().isBlank()) {
                throw new IllegalArgumentException("secretRef must not be blank");
            }
        }
    }
}
