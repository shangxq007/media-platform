#!/usr/bin/env bash
# Stable, bounded verification entry points; filters belong to the immediately preceding project.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in
  identity)
    ./gradlew --no-daemon --console=plain \
      :identity-access-module:test :delivery-module:test \
      :entitlement-module:test --tests '*EntitlementDecisionServiceCollaborationTest' --tests '*CommercialAdmissionServiceTest' \
      :workflow-module:test --tests '*AuthorizationContractTest' --tests '*UserWorkflowDefinitionAuthorizationTest' --tests '*UserWorkflowExecutionRedMatrixTest' \
      :render-module:test --tests '*CanonicalOperationInvocationServiceTest' --tests '*H7FirstRealMediaCutTest' \
        --tests '*H8OperationInvocationBoundaryGuardTest' --tests '*TimelineRevisionSaveServiceIntegrationTest' \
        --tests '*TimelineRevisionSaveServiceSnapshotIntegrationTest' \
      :social-publish-module:test --tests '*SocialAccountReadServiceTest' --tests '*SocialPostReadServiceTest' \
      :platform-app:test --tests '*DeliverySecurityChainTest' --tests '*EnabledAdminSecurityTest' \
        --tests '*TimelineProjectAuthorizationServiceTest' --tests '*CommercialAuthorityDecisionAdapterTest' \
        --tests '*IdentityAuthorizationDenialTransportTest' --tests '*ModularityTest'
    ;;
  compile)
    ./gradlew --no-daemon --console=plain compileJava compileTestJava pfirr1RemediationCheck :platform-app:bootJar
    ;;
  *) echo 'Usage: bash scripts/test-authority-modules.sh identity|compile' >&2; exit 2 ;;
esac
