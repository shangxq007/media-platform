package com.example.platform.config.providerinteroperability;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import java.util.List;

/**
 * Provider Interoperability Authority: the authoritative source of the declared
 * provider-boundary compatibility declarations that provider binding consumes.
 *
 * <p>V0 semantic: no input; the declaration set is derived deterministically from the V0
 * configuration, the empty set is a legal (and the only supported) output, and the format
 * token is version-checked fail-closed.
 *
 * <p>The authority depends only on the V0 configuration carrier and the existing
 * {@link ProviderBoundaryCompatibilityDeclaration} domain type (used purely as an output type).
 * It does not depend on any provider catalog, PF4J type, registry, or provider runtime.
 */
public interface ProviderInteroperabilityAuthority {

    /**
     * The declared provider-boundary compatibility declarations for this process.
     *
     * <p>Deterministic: equal configuration yields an equal result on every call. The returned
     * list is immutable.
     */
    List<ProviderBoundaryCompatibilityDeclaration> declarations();
}
