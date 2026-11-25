package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import java.util.List;

/** Canonical representation of a capability declaration before it receives its digest. */
@org.springframework.modulith.NamedInterface("declaration")
public final class ProviderDeclarationIdentity {
    private ProviderDeclarationIdentity() {}
    public static boolean matches(com.example.platform.execution.compatibility.ProviderCandidate actual,
            com.example.platform.execution.compatibility.ProviderCandidate recorded) {
        return java.util.Arrays.equals(ProviderCandidateCanonicalCodec.encode(actual), ProviderCandidateCanonicalCodec.encode(recorded));
    }

    public static String encode(ProviderExecutionContract contract,
            List<ProviderCapabilitySupport> supports, ProviderStaticCompatibility compatibility) {
        return ProviderCandidateCanonicalCodec.effectiveDeclaration(contract, supports, compatibility);
    }
}
