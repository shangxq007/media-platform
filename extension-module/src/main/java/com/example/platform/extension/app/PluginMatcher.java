package com.example.platform.extension.app;

import com.example.platform.extension.api.port.PluginRegistryPort;
import com.example.platform.extension.api.port.PluginSelectionPolicy;
import com.example.platform.extension.api.port.PluginTenantEnablementPolicy;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.OperationRequest;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginSelectionResult;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic plugin matcher (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 implements the frozen subset of the 11-stage matching pipeline:
 * required capability, capability contract version, handled-object match,
 * platform API compatibility, health eligibility and selection policy.
 * Tenant enablement and authorization are represented as policy ports (not
 * full enforcement). Resource/runtime compatibility is declaration-validated
 * (existing tool checks where they exist).</p>
 *
 * <p>Deterministic order: required capability -&gt; handled object -&gt; platform
 * API compatibility -&gt; capability schema/version compatibility -&gt; tenant
 * enablement (port) -&gt; authorization (port, P1 no-op) -&gt; permissions
 * (declared-match) -&gt; health eligibility -&gt; runtime compatibility
 * (declared) -&gt; resource compatibility (declared) -&gt; selection policy.</p>
 *
 * <p>Selection: explicit plugin ID/version request -&gt; policy priority -&gt;
 * stable identity ordering -&gt; AMBIGUOUS_SELECTION_FAILURE when material
 * ambiguity remains. No first-Spring-bean, registration-order, classpath-order,
 * filesystem-order or random selection; no switch on provider implementation
 * class.</p>
 */
@Service
public class PluginMatcher {

    /** Stable outcome code for no matching provider. */
    public static final String MTC_NO_MATCH = "MTC-NO_MATCH";

    /** Stable outcome code for ambiguous provider selection. */
    public static final String MTC_AMBIGUOUS = "MTC-AMBIGUOUS";

    /** Stable outcome code for unhealthy provider (health eligibility). */
    public static final String HLT_UNHEALTHY = "HLT-UNHEALTHY";

    /** Stable outcome code for disabled provider (health eligibility). */
    public static final String HLT_DISABLED = "HLT-DISABLED";

    private final PluginRegistryPort registry;
    private final PluginHealthRegistry healthRegistry;
    private final PluginSelectionPolicy selectionPolicy;
    private final PluginTenantEnablementPolicy tenantPolicy;

    public PluginMatcher(
            PluginRegistryPort registry,
            PluginHealthRegistry healthRegistry,
            PluginSelectionPolicy selectionPolicy,
            PluginTenantEnablementPolicy tenantPolicy) {
        this.registry = registry;
        this.healthRegistry = healthRegistry;
        this.selectionPolicy = selectionPolicy;
        this.tenantPolicy = tenantPolicy;
    }

    /** Convenience constructor with the default trusted-internal tenant policy. */
    @Autowired
    public PluginMatcher(
            PluginRegistryPort registry,
            PluginHealthRegistry healthRegistry,
            PluginSelectionPolicy selectionPolicy) {
        this(registry, healthRegistry, selectionPolicy, PluginTenantEnablementPolicy.trustedInternal());
    }

    /**
     * Deterministic capability candidate match. Returns all eligible candidates
     * in deterministic order (selection policy priority descending, then stable
     * plugin ID, then version). Matching input/output use stable IDs and value
     * types.
     *
     * @param request matching request
     * @return ordered candidate list (may be empty = MTC-NO_MATCH)
     */
    public List<PluginSelectionResult> match(OperationRequest request) {
        List<PluginSelectionResult> results = new ArrayList<>();
        for (PluginDescriptor descriptor : registry.enumerate()) {
            // Stage 1: required capability + capability contract version.
            Optional<CapabilityDescriptor> capability = descriptor.capabilities().stream()
                    .filter(c -> c.capabilityId().equals(request.requiredCapabilityId()))
                    .filter(c -> c.capabilityContractVersion()
                            .equals(request.requiredCapabilityContractVersion()))
                    .findFirst();
            if (capability.isEmpty()) {
                continue;
            }
            // Stage 2: handled-object match.
            Optional<HandledObjectDescriptor> handledObject = descriptor.handledObjects().stream()
                    .filter(h -> h.objectTypeId().equals(request.handledObjectTypeId()))
                    .findFirst();
            if (handledObject.isEmpty()) {
                continue;
            }
            // Stage 3: platform API compatibility.
            if (!PluginDescriptorValidator.SUPPORTED_PLATFORM_API_VERSIONS
                    .contains(descriptor.platformApiVersion())) {
                continue;
            }
            // Stage 4: tenant enablement (policy port).
            if (request.tenantEnablementContext() != null
                    && !request.tenantEnablementContext().enabled()) {
                continue;
            }
            if (request.tenantEnablementContext() != null && request.tenantEnablementContext().tenantId() != null
                    && !tenantPolicy.isEnabled(descriptor.pluginId(),
                            request.tenantEnablementContext().tenantId())) {
                continue;
            }
            // Stage 5: authorization (policy port; P1 no-op — no invocation path).
            // Stage 6: permissions (declared-match; P1 has no invocation-time
            //          permission enforcement — declared vocabulary validated at
            //          registration).
            // Stage 7: health eligibility.
            PluginHealth health = healthRegistry.healthOf(descriptor.pluginId());
            if (!health.eligible()) {
                continue;
            }
            // Stage 8: runtime compatibility (declared).
            if (descriptor.runtimeRequirements().runtime()
                    != PluginRuntimeRequirement.RuntimeMode.TRUSTED_IN_PROCESS) {
                continue;
            }
            // Stage 9: resource compatibility (declared; validated at registration).
            results.add(new PluginSelectionResult(
                    descriptor.pluginId(),
                    descriptor.pluginVersion(),
                    capability.get().capabilityId(),
                    capability.get().capabilityContractVersion(),
                    handledObject.get().objectTypeId(),
                    health.state()));
        }
        // Stage 10: selection policy (deterministic ordering).
        results.sort(Comparator
                .comparingInt((PluginSelectionResult r) -> selectionPriority(r))
                .reversed()
                .thenComparing(PluginSelectionResult::pluginId)
                .thenComparing(PluginSelectionResult::pluginVersion));
        return List.copyOf(results);
    }

    /**
     * Deterministic single selection. Order: explicit plugin ID/version request
     * -&gt; policy priority -&gt; stable identity ordering. Material ambiguity
     * terminates with AMBIGUOUS_SELECTION_FAILURE.
     *
     * @param request matching request
     * @return the selected candidate
     * @throws IllegalStateException with MTC_NO_MATCH / MTC_AMBIGUOUS when no
     *                               candidate or material ambiguity remains
     */
    public PluginSelectionResult select(OperationRequest request) {
        List<PluginSelectionResult> candidates = match(request);
        if (candidates.isEmpty()) {
            throw new IllegalStateException(MTC_NO_MATCH
                    + ": no plugin matches capability=" + request.requiredCapabilityId()
                    + " version=" + request.requiredCapabilityContractVersion()
                    + " handledObject=" + request.handledObjectTypeId());
        }
        // 1. explicit plugin ID/version request.
        if (request.selectionPolicyContext() != null
                && request.selectionPolicyContext().explicitPluginId() != null) {
            String explicitId = request.selectionPolicyContext().explicitPluginId();
            String explicitVersion = request.selectionPolicyContext().explicitPluginVersion();
            List<PluginSelectionResult> explicit = candidates.stream()
                    .filter(r -> r.pluginId().equals(explicitId))
                    .filter(r -> explicitVersion == null || r.pluginVersion().equals(explicitVersion))
                    .toList();
            if (explicit.size() == 1) {
                return explicit.get(0);
            }
            if (explicit.isEmpty()) {
                throw new IllegalStateException(MTC_NO_MATCH
                        + ": explicit plugin " + explicitId
                        + (explicitVersion != null ? " v" + explicitVersion : "")
                        + " is not a matching candidate");
            }
            // Same plugin ID/version duplicates are rejected at registration, so
            // size > 1 here is unreachable; defensive ambiguity failure.
            throw new IllegalStateException(MTC_AMBIGUOUS
                    + ": explicit plugin " + explicitId + " is ambiguous");
        }
        // 2. policy priority: unique highest-priority candidate is selected;
        //    the priority ordering was applied in match(). Group by priority.
        int topPriority = candidates.stream()
                .mapToInt(this::selectionPriority).max().orElse(0);
        List<PluginSelectionResult> topTier = candidates.stream()
                .filter(r -> selectionPriority(r) == topPriority)
                .toList();
        if (topTier.size() == 1) {
            return topTier.get(0);
        }
        // 3. stable identity ordering applied in match() ordering.
        // 4. material ambiguity remains -> AMBIGUOUS_SELECTION_FAILURE.
        if (candidates.size() > 1) {
            throw new IllegalStateException(MTC_AMBIGUOUS
                    + ": " + candidates.size() + " candidates remain for capability="
                    + request.requiredCapabilityId()
                    + " [" + candidates.stream().map(PluginSelectionResult::pluginId)
                            .sorted().toList() + "]");
        }
        return candidates.get(0);
    }

    private int selectionPriority(PluginSelectionResult result) {
        return registry.findByPluginId(result.pluginId())
                .map(selectionPolicy::priority)
                .orElse(0);
    }
}
