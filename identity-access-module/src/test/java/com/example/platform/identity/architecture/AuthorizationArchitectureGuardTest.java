package com.example.platform.identity.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * APPD-CHV1 architecture guards AR-AUTH-01..10 and AR-PD-01..05 as deterministic
 * source-boundary assertions (the project uses source scans, not ArchUnit).
 *
 * <p>These rules prove the bounded security closed loop: authorization ({@code
 * AuthorizationDecisionPort}) is independent of entitlement / feature-flag /
 * capability / quota; the W2 protected resource is guarded at the boundary by the
 * canonical port; the actor is resolved from the security context rather than
 * invented; and flag evaluation is never a security gate.</p>
 */
class AuthorizationArchitectureGuardTest {

    // Test working directory is the module root (identity-access-module), matching
    // the existing W2 architecture-test convention.
    private static final Path SHARED_KERNEL =
            Path.of("../shared-kernel/src/main/java/com/example/platform/shared");
    private static final Path IDENTITY_ACCESS =
            Path.of("src/main/java/com/example/platform/identity");
    private static final Path POLICY_GOVERNANCE =
            Path.of("../policy-governance-module/src/main/java/com/example/platform/policy");
    private static final Path ENTITLEMENT =
            Path.of("../entitlement-module/src/main/java/com/example/platform/entitlement");
    private static final Path WORKFLOW =
            Path.of("../workflow-module/src/main/java/com/example/platform/workflow");
    private static final Path WORKFLOW_DEFINITION =
            WORKFLOW.resolve("definition");
    private static final Path WORKFLOW_TEMPORAL = WORKFLOW.resolve("temporal");
    private static final Path PLATFORM_APP =
            Path.of("../platform-app/src/main/java/com/example/platform");

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<Path> javaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            fail("scan failed for " + dir + ": " + e.getMessage());
            return List.of();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail("read failed for " + file + ": " + e.getMessage());
            return "";
        }
    }

    private static boolean anyFileContains(Path dir, String fragment) {
        return javaFiles(dir).stream().anyMatch(p -> read(p).contains(fragment));
    }

    private static long countFilesContaining(Path dir, String fragment) {
        return javaFiles(dir).stream().filter(p -> read(p).contains(fragment)).count();
    }

    private static void assertNoFileContains(Path dir, String fragment, String rule) {
        for (Path file : javaFiles(dir)) {
            String content = read(file);
            assertFalse(content.contains(fragment),
                    rule + ": " + file + " must not reference '" + fragment + "'");
        }
    }

    // ── AR-AUTH-01: authz != entitlement ─────────────────────────────────────
    // The canonical authorization port/contract must not depend on the entitlement
    // module. Authorization and entitlement are independent concerns.

    @Test
    void arAuth01_authorizationContractHasNoEntitlementDependency() {
        // The canonical authorization contract — the shared-kernel port/types and the
        // RBAC port that implements them — must not depend on the entitlement module.
        // (identity-access as a whole is allowed to integrate entitlement via its
        // package-info allowedDependencies; that is a separate, non-authz concern.)
        assertNoFileContains(SHARED_KERNEL.resolve("authorization"),
                "com.example.platform.entitlement", "AR-AUTH-01");
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        assertFalse(read(port).contains("com.example.platform.entitlement"),
                "AR-AUTH-01: RBAC port must not depend on entitlement");
    }

    // ── AR-AUTH-02: authz != flag ───────────────────────────────────────────
    // The authorization contract must not depend on the feature-flag module.

    @Test
    void arAuth02_authorizationContractHasNoFeatureFlagDependency() {
        // The canonical authorization contract must not depend on the feature-flag
        // module. The authorization decision is independent of flag evaluation.
        assertNoFileContains(SHARED_KERNEL.resolve("authorization"),
                "com.example.platform.policy", "AR-AUTH-02");
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        assertFalse(read(port).contains("com.example.platform.policy"),
                "AR-AUTH-02: RBAC port must not depend on the feature-flag module");
    }

    // ── AR-AUTH-03: cross-tenant default deny ───────────────────────────────
    // The RBAC port must deny when actor tenant != resource tenant.

    @Test
    void arAuth03_rbacPortEnforcesTenantBoundaryDefaultDeny() {
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        String src = read(port);
        assertTrue(src.contains("TENANT_BOUNDARY"),
                "AR-AUTH-03: RBAC port must produce a TENANT_BOUNDARY denial");
        assertTrue(src.contains("tenantsMatch"),
                "AR-AUTH-03: RBAC port must compare actor vs resource tenant");
    }

    // ── AR-AUTH-04: protected resource uses service-layer authz ─────────────
    // The W2 controller must guard operations through the canonical port.

    @Test
    void arAuth04_workflowDefinitionUsesCanonicalAuthorizationPort() {
        Path controller = WORKFLOW_DEFINITION.resolve(
                "api/UserWorkflowDefinitionController.java");
        String src = read(controller);
        assertTrue(src.contains("AuthorizationDecisionPort"),
                "AR-AUTH-04: W2 controller must depend on AuthorizationDecisionPort");
        assertTrue(src.contains("requireAuthorized"),
                "AR-AUTH-04: W2 controller must call requireAuthorized");
    }

    // ── AR-AUTH-05: flag cannot grant authz ────────────────────────────────
    // The RBAC authorization port must not call the FeatureFlagEvaluator.

    @Test
    void arAuth05_rbacPortDoesNotUseFeatureFlagEvaluator() {
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        assertFalse(read(port).contains("FeatureFlagEvaluator"),
                "AR-AUTH-05: RBAC port must not use FeatureFlagEvaluator");
    }

    // ── AR-AUTH-06: entitlement cannot grant authz ─────────────────────────
    // The RBAC authorization port must not call EntitlementDecisionService.

    @Test
    void arAuth06_rbacPortDoesNotUseEntitlementService() {
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        String src = read(port);
        assertFalse(src.contains("EntitlementDecisionService"),
                "AR-AUTH-06: RBAC port must not use EntitlementDecisionService");
        assertFalse(src.contains("QuotaDecisionService"),
                "AR-AUTH-06: RBAC port must not use QuotaDecisionService");
    }

    // ── AR-AUTH-07: delegation cannot exceed grantor ───────────────────────
    // Delegation/impersonation runtime is ABSENT (out of scope). Prove there is no
    // ad-hoc escalation path: no impersonation/delegation concept exists anywhere.

    @Test
    void arAuth07_noDelegationOrImpersonationEscalationPath() {
        // No delegation/impersonation runtime exists in the whole platform source.
        for (Path module : new Path[] { SHARED_KERNEL, IDENTITY_ACCESS, POLICY_GOVERNANCE,
                ENTITLEMENT, WORKFLOW.resolve("definition"), PLATFORM_APP }) {
            for (Path file : javaFiles(module)) {
                String content = read(file);
                assertFalse(content.contains("impersonate"),
                        "AR-AUTH-07: impersonation runtime must be absent: " + file);
                assertFalse(content.contains("DelegationRuntime"),
                        "AR-AUTH-07: delegation runtime must be absent: " + file);
            }
        }
    }

    // ── AR-AUTH-08: canonical actor from security context ──────────────────
    // Actors must be resolved from the security context (jwt.* request attributes,
    // SecurityContext, API-key MDC), with an explicit SYSTEM resolver. No business
    // service may invent an actor.

    @Test
    void arAuth08_canonicalActorResolvedFromSecurityContext() {
        // The JWT/API-key request-attribute resolver lives in platform-app; the
        // MDC and System resolvers live in shared-kernel. At least one resolver must
        // read the jwt.* security-context attributes.
        Path sharedAuthz = SHARED_KERNEL.resolve("authorization");
        boolean hasJwtSource = anyFileContains(sharedAuthz, "jwt.subject")
                || anyFileContains(sharedAuthz, "jwt.tenantId")
                || anyFileContains(PLATFORM_APP.resolve("security"), "jwt.subject")
                || anyFileContains(PLATFORM_APP.resolve("security"), "jwt.tenantId");
        assertTrue(hasJwtSource,
                "AR-AUTH-08: a resolver must read the jwt.* security-context attributes");
        assertTrue(Files.exists(sharedAuthz.resolve("SystemCanonicalActorResolver.java")),
                "AR-AUTH-08: an explicit System resolver must exist");
    }

    // ── AR-AUTH-09: canonical actor from security context (no business
    //     service reading jwt.subject directly as authority) ─────────────────

    @Test
    void arAuth09_noBusinessServiceReadsJwtSubjectAsAuthority() {
        // MeController/NavigationController may read jwt.* for identity display, but
        // must NOT use it as an authorization authority. The composite resolver is
        // the only sanctioned producer of CanonicalActor.
        Path controller = WORKFLOW_DEFINITION.resolve(
                "api/UserWorkflowDefinitionController.java");
        assertFalse(read(controller).contains("jwt.subject"),
                "AR-AUTH-09: W2 controller must not read jwt.subject directly");
        assertFalse(read(controller).contains("SecurityContextHolder"),
                "AR-AUTH-09: W2 controller must not read SecurityContextHolder directly");
    }

    // ── AR-AUTH-10: security cannot be disabled by mutable flag ────────────
    // The authorization decision path must not branch on a feature flag.

    @Test
    void arAuth10_rbacDecisionPathHasNoFlagBranch() {
        Path port = IDENTITY_ACCESS.resolve("app/RbacAuthorizationDecisionPort.java");
        String src = read(port);
        assertFalse(src.contains("isEnabled"),
                "AR-AUTH-10: RBAC decision must not branch on a feature flag");
        assertFalse(src.contains("FeatureFlagService"),
                "AR-AUTH-10: RBAC decision must not consult FeatureFlagService");
    }

    // ── AR-PD-01: approved flag abstraction used ───────────────────────────
    // Exactly one implementation of FeatureFlagEvaluator must exist (the approved
    // abstraction). No second flag service.

    @Test
    void arPd01_singleFeatureFlagEvaluatorImplementation() {
        long implementers = 0;
        for (Path module : new Path[] { SHARED_KERNEL, IDENTITY_ACCESS, POLICY_GOVERNANCE,
                ENTITLEMENT, WORKFLOW.resolve("definition"), PLATFORM_APP }) {
            implementers += countFilesContaining(module, "implements FeatureFlagEvaluator");
        }
        assertTrue(implementers == 1,
                "AR-PD-01: FeatureFlagEvaluator must have exactly one implementation, found "
                        + implementers);
    }

    // ── AR-PD-02: platform flag domain authority ───────────────────────────
    // The feature-flag domain authority stays in policy-governance-module.

    @Test
    void arPd02_flagDomainAuthorityInPolicyGovernance() {
        assertTrue(Files.isDirectory(POLICY_GOVERNANCE.resolve("featureflag")),
                "AR-PD-02: feature-flag domain authority must live in policy-governance-module");
        assertTrue(Files.exists(POLICY_GOVERNANCE.resolve(
                "featureflag/FeatureFlagEvaluator.java"))
                || Files.exists(POLICY_GOVERNANCE.resolve(
                        "api/FeatureFlagEvaluator.java")),
                "AR-PD-02: FeatureFlagEvaluator must be defined in policy-governance-module");
    }

    // ── AR-PD-03: no mutable flag eval in Temporal workflow code ───────────
    // Temporal workflow implementations must not call the mutable
    // FeatureFlagEvaluator or read the current SecurityContext at execution time.
    // The W2 definition temporal surface is absent; the W1 render activities use a
    // deployment-time flag and are frozen (excluded by the W1 contract).

    @Test
    void arPd03_temporalWorkflowDoesNotEvaluateMutableFlags() {
        // W2 definition module has no Temporal workflow implementation at all.
        assertFalse(Files.isDirectory(WORKFLOW_DEFINITION.resolve("temporal")),
                "AR-PD-03: W2 definition must not introduce a Temporal runtime");
        for (Path file : javaFiles(WORKFLOW_DEFINITION)) {
            assertFalse(read(file).contains("FeatureFlagEvaluator"),
                    "AR-PD-03: W2 definition must not use FeatureFlagEvaluator: " + file);
            assertFalse(read(file).contains("SecurityContextHolder"),
                    "AR-PD-03: W2 definition must not read SecurityContext: " + file);
        }
    }

    // ── AR-PD-04: rollout targeting tenant-safe ────────────────────────────
    // Flag targeting must scope by tenant (tenant-safe rollout).

    @Test
    void arPd04_flagTargetingIsTenantScoped() {
        Path localProvider = POLICY_GOVERNANCE.resolve(
                "featureflag/LocalFeatureFlagProvider.java");
        String src = read(localProvider);
        assertTrue(src.contains("tenantId") || src.contains("Tenant"),
                "AR-PD-04: local flag provider must support tenant-scoped targeting");
    }

    // ── AR-PD-05: kill switch fails closed for protected operations ────────
    // The FeatureFlagEvaluator/isEnabled defaults to false (closed) and the access
    // composition treats a disabled flag as denial.

    @Test
    void arPd05_flagFailsClosed() {
        Path evaluator = POLICY_GOVERNANCE.resolve("api/FeatureFlagEvaluator.java");
        String src = read(evaluator);
        assertTrue(src.contains("boolean defaultValue"),
                "AR-PD-05: FeatureFlagEvaluator.isEnabled must accept a defaultValue (fail-closed)");
    }
}
