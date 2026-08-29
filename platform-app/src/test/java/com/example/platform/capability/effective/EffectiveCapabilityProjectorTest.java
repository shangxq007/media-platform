package com.example.platform.capability.effective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveCapabilityProjectorTest {

    private static final Instant DECIDED_AT = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void allFiveIndependentSourcesAllowProducesEffectiveView() {
        AuthorityDecisionInput capability = allow(
                EffectiveCapabilitySource.CAPABILITY_LIFECYCLE, "CapabilityRegistry", "cap.video.export");
        AuthorityDecisionInput runtime = allow(
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY, "H1RuntimeAvailability", "runtime-proof-7");
        AuthorityDecisionInput entitlement = allow(
                EffectiveCapabilitySource.H5_ENTITLEMENT, "Entitlement", "grant-ref-11");
        AuthorityDecisionInput quota = new AuthorityDecisionInput(
                EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA,
                "Quota",
                "render.minutes",
                AuthorityDecisionResult.ALLOW,
                List.of("WITHIN_QUOTA"),
                "quota-v4",
                "quota-decision-13",
                DECIDED_AT,
                List.of(new AuthorityProvenance("QuotaUsageAuthority", "QUOTA_DECISION", "quota-13")),
                false,
                new QuotaDecisionDetails("render.minutes", 100, 40, 5));
        AuthorityDecisionInput policy = allow(
                EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY, "RoleWorkspacePolicy", "policy-ref-17");

        EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        "cap.video.export", capability, runtime, entitlement, quota, policy));

        assertEquals(EffectiveCapabilityStatus.EFFECTIVE, view.status());
        assertEquals("EFFECTIVE_CAPABILITY_VIEW_V1", view.projectionVersion());
        assertEquals(
                List.of(capability, runtime, entitlement, quota, policy),
                view.sourceDecisions());
    }

    @Test
    void missingOrInactiveCapabilityDeniesAndPreservesOtherSourceInputs() {
        for (String reason : List.of("CAPABILITY_MISSING", "CAPABILITY_INACTIVE")) {
            EffectiveCapabilityInputs inputs = allAllowInputs();
            AuthorityDecisionInput deniedCapability = decision(
                    EffectiveCapabilitySource.CAPABILITY_LIFECYCLE,
                    "CapabilityRegistry",
                    AuthorityDecisionResult.DENY,
                    reason,
                    null);

            EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                    new EffectiveCapabilityInputs(
                            inputs.capabilityId(),
                            deniedCapability,
                            inputs.runtimeAvailability(),
                            inputs.entitlement(),
                            inputs.quota(),
                            inputs.roleWorkspacePolicy()));

            assertEquals(EffectiveCapabilityStatus.DENIED, view.status());
            assertEquals(5, view.sourceDecisions().size());
            assertEquals(inputs.quota(), view.sourceDecisions().get(3));
            assertTrue(hasReason(view, EffectiveCapabilitySource.CAPABILITY_LIFECYCLE, reason));
        }
    }

    @Test
    void h1UnavailableDeniesWithoutRewritingItsReasonOrProvenance() {
        EffectiveCapabilityInputs inputs = allAllowInputs();
        AuthorityProvenance runtimeProof =
                new AuthorityProvenance("H1RuntimeAvailability", "ELIGIBILITY_PROOF", "h1-proof-44");
        AuthorityDecisionInput unavailable = new AuthorityDecisionInput(
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "H1RuntimeAvailability",
                "cap.video.export",
                AuthorityDecisionResult.DENY,
                List.of("WORKER_DRAINING"),
                "h1-v8",
                "runtime-decision-44",
                DECIDED_AT,
                List.of(runtimeProof),
                false,
                null);

        EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        inputs.capabilityId(),
                        inputs.capabilityLifecycle(),
                        unavailable,
                        inputs.entitlement(),
                        inputs.quota(),
                        inputs.roleWorkspacePolicy()));

        assertEquals(EffectiveCapabilityStatus.DENIED, view.status());
        assertTrue(hasReason(view, EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY, "WORKER_DRAINING"));
        assertFalse(view.reasons().stream().anyMatch(reason -> reason.code().equals("QUOTA_EXCEEDED")));
        assertEquals(List.of(runtimeProof), view.sourceDecisions().get(1).provenance());
    }

    @Test
    void h1UnknownStaleOrMissingFailsClosed() {
        EffectiveCapabilityInputs inputs = allAllowInputs();
        AuthorityDecisionInput unknown = decision(
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "H1RuntimeAvailability",
                AuthorityDecisionResult.UNKNOWN,
                "RUNTIME_STATE_UNKNOWN",
                null);
        AuthorityDecisionInput stale = new AuthorityDecisionInput(
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "H1RuntimeAvailability",
                "cap.video.export",
                AuthorityDecisionResult.ALLOW,
                List.of("AVAILABLE"),
                "h1-v8",
                "runtime-stale-45",
                DECIDED_AT,
                List.of(),
                true,
                null);

        assertEquals(
                EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED,
                projectWithRuntime(inputs, unknown).status());
        EffectiveCapabilityView staleView = projectWithRuntime(inputs, stale);
        assertEquals(EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED, staleView.status());
        assertTrue(hasReason(
                staleView,
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "STALE_SOURCE_DECISION"));
        EffectiveCapabilityView missingView = projectWithRuntime(inputs, null);
        assertEquals(EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED, missingView.status());
        assertTrue(hasReason(
                missingView,
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "MISSING_SOURCE_DECISION"));
    }

    @Test
    void entitlementAndQuotaDenyIndependently() {
        EffectiveCapabilityInputs inputs = allAllowInputs();
        AuthorityDecisionInput entitlementDenied = decision(
                EffectiveCapabilitySource.H5_ENTITLEMENT,
                "Entitlement",
                AuthorityDecisionResult.DENY,
                "NOT_ENTITLED",
                null);
        EffectiveCapabilityView entitlementView = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        inputs.capabilityId(),
                        inputs.capabilityLifecycle(),
                        inputs.runtimeAvailability(),
                        entitlementDenied,
                        inputs.quota(),
                        inputs.roleWorkspacePolicy()));
        assertEquals(EffectiveCapabilityStatus.DENIED, entitlementView.status());
        assertEquals(AuthorityDecisionResult.ALLOW, entitlementView.sourceDecisions().get(3).result());

        QuotaDecisionDetails details = new QuotaDecisionDetails("render.minutes", 100, 98, 5);
        AuthorityDecisionInput quotaDenied = decision(
                EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA,
                "Quota",
                AuthorityDecisionResult.DENY,
                "QUOTA_EXCEEDED",
                details);
        EffectiveCapabilityView quotaView = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        inputs.capabilityId(),
                        inputs.capabilityLifecycle(),
                        inputs.runtimeAvailability(),
                        inputs.entitlement(),
                        quotaDenied,
                        inputs.roleWorkspacePolicy()));
        assertEquals(EffectiveCapabilityStatus.DENIED, quotaView.status());
        assertEquals(AuthorityDecisionResult.ALLOW, quotaView.sourceDecisions().get(2).result());
        assertEquals(details, quotaView.sourceDecisions().get(3).quotaDetails());
    }

    @Test
    void roleWorkspacePolicyDenialIsIndependent() {
        EffectiveCapabilityInputs inputs = allAllowInputs();
        AuthorityDecisionInput deniedPolicy = decision(
                EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY,
                "RoleWorkspacePolicy",
                AuthorityDecisionResult.DENY,
                "WORKSPACE_ROLE_DENIED",
                null);

        EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        inputs.capabilityId(),
                        inputs.capabilityLifecycle(),
                        inputs.runtimeAvailability(),
                        inputs.entitlement(),
                        inputs.quota(),
                        deniedPolicy));

        assertEquals(EffectiveCapabilityStatus.DENIED, view.status());
        assertTrue(hasReason(
                view, EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY, "WORKSPACE_ROLE_DENIED"));
    }

    @Test
    void everyMissingSourceFailsClosedAndNamesThatSource() {
        EffectiveCapabilityInputs allowed = allAllowInputs();
        for (EffectiveCapabilitySource missing : EffectiveCapabilitySource.values()) {
            EffectiveCapabilityInputs inputs = new EffectiveCapabilityInputs(
                    allowed.capabilityId(),
                    missing == EffectiveCapabilitySource.CAPABILITY_LIFECYCLE
                            ? null : allowed.capabilityLifecycle(),
                    missing == EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY
                            ? null : allowed.runtimeAvailability(),
                    missing == EffectiveCapabilitySource.H5_ENTITLEMENT
                            ? null : allowed.entitlement(),
                    missing == EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA
                            ? null : allowed.quota(),
                    missing == EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY
                            ? null : allowed.roleWorkspacePolicy());

            EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(inputs);

            assertEquals(EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED, view.status());
            assertTrue(hasReason(view, missing, "MISSING_SOURCE_DECISION"));
        }
    }

    @Test
    void projectionNeverUpgradesDeniedOrUnknownSource() {
        EffectiveCapabilityInputs inputs = allAllowInputs();
        AuthorityDecisionInput deniedEntitlement = decision(
                EffectiveCapabilitySource.H5_ENTITLEMENT,
                "Entitlement",
                AuthorityDecisionResult.DENY,
                "NOT_ENTITLED",
                null);
        AuthorityDecisionInput unknownRuntime = decision(
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                "H1RuntimeAvailability",
                AuthorityDecisionResult.UNKNOWN,
                "HOST_STATE_UNKNOWN",
                null);

        EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        inputs.capabilityId(),
                        inputs.capabilityLifecycle(),
                        unknownRuntime,
                        deniedEntitlement,
                        inputs.quota(),
                        inputs.roleWorkspacePolicy()));

        assertEquals(EffectiveCapabilityStatus.UNKNOWN_FAIL_CLOSED, view.status());
        assertFalse(view.effective());
    }

    @Test
    void sourceAndReasonOrderIsDeterministicAndCollectionsAreImmutable() {
        List<String> capabilityReasons = new ArrayList<>(
                List.of("CAPABILITY_EXISTS", "CAPABILITY_ACTIVE"));
        List<AuthorityProvenance> capabilityProvenance = new ArrayList<>(List.of(
                new AuthorityProvenance("CapabilityRegistry", "REGISTRY_VERSION", "registry-9")));
        AuthorityDecisionInput capability = new AuthorityDecisionInput(
                EffectiveCapabilitySource.CAPABILITY_LIFECYCLE,
                "CapabilityRegistry",
                "cap.video.export",
                AuthorityDecisionResult.ALLOW,
                capabilityReasons,
                "registry-v9",
                "capability-9",
                DECIDED_AT,
                capabilityProvenance,
                false,
                null);
        EffectiveCapabilityInputs allowed = allAllowInputs();
        EffectiveCapabilityView view = new EffectiveCapabilityProjector().project(
                new EffectiveCapabilityInputs(
                        allowed.capabilityId(),
                        capability,
                        allowed.runtimeAvailability(),
                        allowed.entitlement(),
                        allowed.quota(),
                        allowed.roleWorkspacePolicy()));

        capabilityReasons.add("MUTATED_AFTER_CONSTRUCTION");
        capabilityProvenance.clear();

        assertEquals(List.of(
                EffectiveCapabilitySource.CAPABILITY_LIFECYCLE,
                EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY,
                EffectiveCapabilitySource.H5_ENTITLEMENT,
                EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA,
                EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY),
                view.sourceDecisions().stream().map(AuthorityDecisionInput::source).toList());
        assertEquals(List.of(
                "CAPABILITY_EXISTS",
                "CAPABILITY_ACTIVE",
                "ALLOWED",
                "ALLOWED",
                "WITHIN_QUOTA",
                "ALLOWED"),
                view.reasons().stream().map(EffectiveCapabilityReason::code).toList());
        assertEquals(List.of("CAPABILITY_EXISTS", "CAPABILITY_ACTIVE"), capability.reasonCodes());
        assertEquals(1, capability.provenance().size());
        assertThrows(UnsupportedOperationException.class, () -> view.sourceDecisions().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.reasons().clear());
        assertThrows(UnsupportedOperationException.class, () -> capability.reasonCodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> capability.provenance().clear());
    }

    private static AuthorityDecisionInput allow(
            EffectiveCapabilitySource source, String authority, String reference) {
        return new AuthorityDecisionInput(
                source,
                authority,
                "cap.video.export",
                AuthorityDecisionResult.ALLOW,
                List.of("ALLOWED"),
                authority + "-v1",
                reference,
                DECIDED_AT,
                List.of(new AuthorityProvenance(authority, "DECISION", reference)),
                false,
                null);
    }

    private static AuthorityDecisionInput decision(
            EffectiveCapabilitySource source,
            String authority,
            AuthorityDecisionResult result,
            String reason,
            QuotaDecisionDetails quotaDetails) {
        return new AuthorityDecisionInput(
                source,
                authority,
                source == EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA
                        ? "render.minutes" : "cap.video.export",
                result,
                List.of(reason),
                authority + "-v1",
                authority + "-decision",
                DECIDED_AT,
                List.of(new AuthorityProvenance(authority, "DECISION", authority + "-evidence")),
                false,
                quotaDetails);
    }

    private static EffectiveCapabilityInputs allAllowInputs() {
        return new EffectiveCapabilityInputs(
                "cap.video.export",
                allow(EffectiveCapabilitySource.CAPABILITY_LIFECYCLE, "CapabilityRegistry", "capability-ref"),
                allow(EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY, "H1RuntimeAvailability", "h1-ref"),
                allow(EffectiveCapabilitySource.H5_ENTITLEMENT, "Entitlement", "entitlement-ref"),
                new AuthorityDecisionInput(
                        EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA,
                        "Quota",
                        "render.minutes",
                        AuthorityDecisionResult.ALLOW,
                        List.of("WITHIN_QUOTA"),
                        "quota-v1",
                        "quota-ref",
                        DECIDED_AT,
                        List.of(),
                        false,
                        new QuotaDecisionDetails("render.minutes", 100, 40, 5)),
                allow(EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY, "RoleWorkspacePolicy", "policy-ref"));
    }

    private static EffectiveCapabilityView projectWithRuntime(
            EffectiveCapabilityInputs inputs, AuthorityDecisionInput runtime) {
        return new EffectiveCapabilityProjector().project(new EffectiveCapabilityInputs(
                inputs.capabilityId(),
                inputs.capabilityLifecycle(),
                runtime,
                inputs.entitlement(),
                inputs.quota(),
                inputs.roleWorkspacePolicy()));
    }

    private static boolean hasReason(
            EffectiveCapabilityView view, EffectiveCapabilitySource source, String code) {
        return view.reasons().stream()
                .anyMatch(reason -> reason.source() == source && reason.code().equals(code));
    }
}
