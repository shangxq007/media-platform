package com.example.platform.render.app.planner;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.app.capability.CapabilityCatalogService;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Capability Resolution Service (PRE-#21 W2, contract C4/C5).
 *
 * <p>AUTHORITY: this service RESOLVES DECLARED capability requirements. It
 * never invents, derives, or guesses semantic requirements from product type,
 * task type, provider type, or implementation choice. Semantic requirements
 * are declared by their semantic consumer (OperationDefinition and
 * equivalents) via {@link CapabilityRequirement}.
 *
 * <p>Frozen chain:
 * <pre>
 *   Semantic Consumer / OperationDefinition
 *           → CapabilityRequirement (declared)
 *           → Capability Registry / Catalog
 *           → this resolver (filter/validate/select)
 *           → CapabilityImplementation / Provider
 * </pre>
 *
 * <p>The legacy productType→capability switch mapping has been removed
 * (CLEAN FORWARD): zero callers, zero definitions, no wrapper, no dual
 * authority.
 */
@Service
public class CapabilityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityResolutionService.class);
    private final CapabilityCatalogService catalog;

    public CapabilityResolutionService(CapabilityCatalogService catalog) {
        this.catalog = catalog;
    }

    public record ResolutionResult(String capabilityId, String producerId,
                                     String selectionReason, boolean resolved) {}

    /**
     * Resolves a DECLARED capability requirement against the catalog.
     * The requirement is the semantic authority; the resolver only discovers
     * and selects eligible implementations.
     */
    public ResolutionResult resolve(CapabilityRequirement requirement) {
        String capabilityId = requirement.capabilityId().value();
        var candidate = catalog.resolvePreferred(capabilityId)
                .orElseGet(() -> catalog.resolve(capabilityId).orElse(null));
        if (candidate == null) {
            return new ResolutionResult(capabilityId, null,
                    "No producer in catalog for " + capabilityId, false);
        }
        String reason = candidate.preferred()
                ? "preferred producer for " + capabilityId
                : "highest priority (" + candidate.priority() + ") for " + capabilityId;
        return new ResolutionResult(capabilityId, candidate.producerId(), reason, true);
    }

    public String explain(CapabilityRequirement requirement) {
        var res = resolve(requirement);
        if (!res.resolved()) {
            return "Unresolved: " + requirement.capabilityId().value()
                    + " — " + res.selectionReason();
        }
        return requirement.capabilityId().value()
                + " → Producer " + res.producerId()
                + " (" + res.selectionReason() + ")";
    }
}
