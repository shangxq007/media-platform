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
  observation)
    ./gradlew --no-daemon --console=plain \
      :observability-module:test :audit-compliance-module:test \
      :identity-access-module:test --tests '*ApiKeyAuthenticationContextTest' \
        --tests '*IdentityAccessServiceTest' --tests '*AuthorizationArchitectureGuardTest' \
      :platform-app:test --tests '*ObservationBoundaryTest' --tests '*JwtAuthFilterTest' \
        --tests '*OAuth2RequestContextFilterTest' --tests '*DeliverySecurityChainTest' \
        --tests '*EnabledAdminSecurityTest' --tests '*AdminAuditHelperTest' --tests '*ModularityTest'
    ;;
  billing)
    ./gradlew --no-daemon --console=plain \
      :billing-module:test --tests '*Usage*' --tests '*BillingConsumptionBoundaryTest' \
      :outbox-event-module:test --tests '*OutboxEventServiceTest' \
      :ai-module:test --tests '*AiUsageEmissionTest' \
      :render-module:test --tests '*RenderUsageEmissionTest' \
      :extension-module:test --tests '*RuntimeUsageEmitterTest' --tests '*RuntimeObservedUsageEmitterTest' \
        --tests '*PluginRuntimeRedMatrixTest' --tests '*PluginRuntimeArchitectureGuardTest' \
      :platform-app:test --tests '*BillingUsageCompositionTest' --tests '*SandboxRuntimeConvergenceTest' --tests '*ModularityTest'
    ;;
  execution)
    ./gradlew --no-daemon --console=plain \
      :bmf-provider-module:test :provider-plugin-runtime-module:test :outbox-event-module:test \
      :render-module:compileTestJava \
      :platform-app:test --tests '*ProviderRuntimeExecutionCompositionTest' --tests '*ModularityTest'
    ;;
  compile)
    ./gradlew --no-daemon --console=plain compileJava compileTestJava pfirr1RemediationCheck :platform-app:bootJar
    ;;
  *) echo 'Usage: bash scripts/test-authority-modules.sh identity|observation|billing|execution|compile' >&2; exit 2 ;;
esac
