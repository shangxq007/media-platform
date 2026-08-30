package com.example.platform.capability.effective;

import java.util.ArrayList;
import java.util.List;

/** Pure intersection over source-owned decisions. */
public final class EffectiveCapabilityProjector {

    public static final String PROJECTION_VERSION = "EFFECTIVE_CAPABILITY_VIEW_V1";

    public EffectiveCapabilityView project(EffectiveCapabilityInputs inputs) {
        AuthorityDecisionInput[] orderedInputs = {
                inputs.capabilityLifecycle(),
                inputs.runtimeAvailability(),
                inputs.entitlement(),
                inputs.quota(),
                inputs.roleWorkspacePolicy()
        };
        List<AuthorityDecisionInput> decisions = new ArrayList<>(orderedInputs.length);
        List<EffectiveCapabilityReason> reasons = new ArrayList<>();
        boolean unknown = false;
        boolean denied = false;
        EffectiveCapabilitySource[] sources = EffectiveCapabilitySource.values();
        for (int index = 0; index < orderedInputs.length; index++) {
            AuthorityDecisionInput decision = orderedInputs[index];
            EffectiveCapabilitySource source = sources[index];
            if (decision == null) {
                unknown = true;
                reasons.add(new EffectiveCapabilityReason(
                        source,
                        source.name(),
                        "MISSING_SOURCE_DECISION",
                        "MISSING"));
                continue;
            }
            decisions.add(decision);
            decision.reasonCodes().forEach(code -> reasons.add(new EffectiveCapabilityReason(
                    source,
                    decision.authorityName(),
                    code,
                    decision.decisionReference())));
            if (decision.stale()) {
                unknown = true;
                reasons.add(new EffectiveCapabilityReason(
                        source,
                        decision.authorityName(),
                        "STALE_SOURCE_DECISION",
                        decision.decisionReference()));
            } else if (decision.result() == AuthorityDecisionResult.UNKNOWN) {
                unknown = true;
            } else if (decision.result() == AuthorityDecisionResult.DENY) {
                denied = true;
            }
        }
        EffectiveCapabilityStatus status = unknown
                ? EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED
                : denied ? EffectiveCapabilityStatus.DENIED : EffectiveCapabilityStatus.EFFECTIVE;
        return new EffectiveCapabilityView(
                PROJECTION_VERSION,
                inputs.capabilityId(),
                status,
                decisions,
                reasons);
    }
}
