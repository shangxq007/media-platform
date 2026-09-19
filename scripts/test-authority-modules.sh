#!/usr/bin/env bash
# Stable, bounded verification entry points; filters belong to the immediately preceding project.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in
  workflow-plan)
    ./gradlew --offline --no-daemon --console=plain :workflow-module:test \
      --tests '*WorkflowPlanTest' --tests '*PlanWalkSdkTest' --tests '*UserWorkflowNodeTypeVocabularyTest'
    ;;
  identity)
    ./gradlew --no-daemon --console=plain \
      :identity-access-module:test :delivery-module:test \
      :timeline-module:test --tests 'com.example.platform.timeline.app.*' --tests '*TimelineCanonicalProductionBoundaryTest' \
      :entitlement-module:test --tests '*EntitlementDecisionServiceCollaborationTest' --tests '*CommercialAdmissionServiceTest' \
      :workflow-module:test --tests '*AuthorizationContractTest' --tests '*UserWorkflowDefinitionAuthorizationTest' --tests '*UserWorkflowExecutionRedMatrixTest' \
      :render-module:test --tests '*CanonicalOperationInvocationServiceTest' --tests '*H7FirstRealMediaCutTest' \
        --tests '*H8OperationInvocationBoundaryGuardTest' --tests '*TimelineRevisionSaveServiceIntegrationTest' \
        --tests '*TimelineRevisionSaveServiceSnapshotIntegrationTest' \
      :social-publish-module:test --tests '*SocialAccountReadServiceTest' --tests '*SocialPostReadServiceTest' \
      :platform-app:test --tests '*DeliverySecurityChainTest' --tests '*EnabledAdminSecurityTest' \
        --tests '*TimelineReviewOwnerIntegrationTest' --tests '*TimelineEventBoundaryTest' --tests '*H7V2CanonicalOwnershipInvariantTest' --tests '*TimelineAuthorityBoundaryTest' --tests '*TimelineReviewControllerTest' --tests '*TimelineWorkbenchControllerTest' \
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
      :bmf-provider-module:test :provider-plugin-runtime-module:test \
      :worker-fabric-module:test --tests '*RuntimeClosedLoopConformanceTest' --tests '*RuntimeClosedLoopArchitectureGuardTest' \
      :remote-render-worker:test --tests '*RemoteRenderWorkerTest' \
      :outbox-event-module:test \
      :storage-module:test :media-module:test \
      :artifact-module:test --tests '*ArtifactCommitServiceTest' --tests '*ArtifactApplicationServiceTest' --tests '*JooqArtifactCommitServiceProvenance*' \
      :render-module:test --tests '*RenderArtifactStorageServiceTest' --tests '*RenderOutputRegistrationServiceTest' --tests '*RawMediaProductRegistrationFacadeTest' --tests '*PreviewArtifactQueryServiceTest' --tests 'com.example.platform.render.app.asset.*' --tests '*AssetServiceTest' --tests '*MediaAssetProbeServiceTest' --tests '*TimelineResolverRejectionTest' --tests '*TimelineSpecResolverTest' --tests '*TimelineScriptParserTest' --tests '*TimelineScriptParserEffectsTest' \
        --tests '*RenderJobFailureInitiatorTest' --tests '*RenderJobFailureDurabilityIntegrationTest' --tests '*StaleRenderJobCompensatorTest' --tests '*RenderJobLeaseServiceTest' \
        --tests '*RenderOrchestratorServiceCharacterizationTest' --tests '*RenderPipelineE2ECharacterizationTest' \
        --tests '*RenderCacheHashInvalidationNotifierTest' --tests '*RenderInitiatorContractTest' --tests '*C1Cnm1Cr1RateContractTest' --tests '*FrameRateTest' --tests 'com.example.platform.render.ir.*' --tests '*OpenTimelineioAdapterTest' --tests '*OpenTimelineioAdapterMetadataTest' \
      :platform-app:test --tests '*ProviderRuntimeExecutionCompositionTest' --tests '*RenderOutputAcceptanceTest' --tests '*RenderTimeBoundaryTest' --tests '*OtherDomainEventBoundaryTest' --tests '*PreviewUploadHttpTest' --tests '*PreviewCoordinationBoundaryTest' --tests '*MediaOwnerIntegrationTest' --tests '*MediaAuthorityBoundaryTest' --tests 'com.example.platform.ingest.*' --tests 'com.example.platform.web.assets.*' --tests '*ModularityTest'
    ;;
  outbox)
    ./gradlew --no-daemon --console=plain \
      :outbox-event-module:test \
      :billing-module:test --tests '*Usage*' --tests '*CostObservationEmissionServiceTest' --tests '*BillingConsumptionBoundaryTest' \
      :notification-module:test :render-module:test --tests '*RenderOutboxEventsTest' --tests '*RenderLifecycleBoundaryTest' \
      :platform-app:test --tests '*BillingUsageCompositionTest' --tests '*OutboxNotificationCompositionTest' --tests '*NotificationIngressPersistenceTest' --tests '*ModularityTest'
    ;;
  render-read)
    ./gradlew --no-daemon --console=plain \
      :identity-access-module:test --tests '*ProjectReadAuthorizationTest' --tests '*MultiTenancyIsolationTest' --tests '*ProjectImportServiceTest' --tests '*ProjectExportServiceTest' \
      :render-module:test --tests '*RenderJobServiceTest' --tests '*RenderJobReadAuthorizationTest' --tests '*RenderControllerContractTest' \
      :federation-query-module:test --tests '*AdminDashboardGraphQLResolverTest' --tests '*MonitoringFeedbackGraphQLResolverTest' --tests '*ExportPanelGraphQLResolverTest' --tests '*ExportPanelStateQueryTest' \
      :platform-app:test --tests '*RenderReadHttpTest' --tests '*RenderControllerTest' --tests '*ModularityTest'
    ;;
  affected)
    authority_selection=$(python3 scripts/ci/change_impact_classifier.py --base "${2:?base required}" --head "${3:?head required}" --json)
    while read -r authority_group; do
      [[ -z "$authority_group" ]] || bash scripts/test-authority-modules.sh "$authority_group"
    done < <(printf '%s' "$authority_selection" | python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)["authority_test_groups"]))')
    ;;
  compile)
    ./gradlew --no-daemon --console=plain compileJava compileTestJava pfirr1RemediationCheck :platform-app:bootJar
    ;;
  *) echo 'Usage: bash scripts/test-authority-modules.sh identity|observation|billing|execution|outbox|render-read|workflow-plan|compile|affected BASE HEAD' >&2; exit 2 ;;
esac
