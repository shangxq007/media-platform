# @ConditionalOnBean Removal Audit

## Classification of All Removed Annotifacts

### Category A: Wrong condition - should stay removed (no guard needed)

These are `@Repository` classes that use jOOQ `DSLContext` or JDBC `JdbcTemplate`.
The condition was wrong because Spring's `@ConditionalOnBean` evaluates before
all beans are registered, causing the bean to be skipped even when its dependency
is available. These should be unconditional since their dependency (DSLContext/JdbcTemplate)
is a core infrastructure bean that is always present when the module is included.

#### A1. DSLContext-dependent repositories (all removed, should stay removed):

| File | Class | Dependency | Verdict |
|------|-------|-----------|---------|
| identity-access-module/.../WorkspaceRepository.java | WorkspaceRepository | DSLContext | A - keep removed |
| identity-access-module/.../WorkspaceMemberRepository.java | WorkspaceMemberRepository | DSLContext | A - keep removed |
| identity-access-module/.../WorkspaceGroupRepository.java | WorkspaceGroupRepository | DSLContext | A - keep removed |
| identity-access-module/.../RoleRepository.java | RoleRepository | DSLContext | A - keep removed |
| artifact-catalog-module/.../ArtifactCatalogRepository.java | ArtifactCatalogRepository | DSLContext | A - keep removed |
| artifact-catalog-module/.../ArtifactRelationRepository.java | ArtifactRelationRepository | DSLContext | A - keep removed |
| billing-module/.../BillingInvoiceRepository.java | BillingInvoiceRepository | DSLContext | A - keep removed |
| billing-module/.../BillingLedgerJdbcRepository.java | BillingLedgerJdbcRepository | DSLContext | A - keep removed |
| billing-module/.../SubscriptionContractRepository.java | SubscriptionContractRepository | DSLContext | A - keep removed |
| billing-module/.../SubscriptionJdbcRepository.java | SubscriptionJdbcRepository | DSLContext | A - keep removed |
| billing-module/.../CreditWalletJdbcRepository.java | CreditWalletJdbcRepository | DSLContext | A - keep removed |
| commerce-module/.../CheckoutSessionRepository.java | CheckoutSessionRepository | DSLContext | A - keep removed |
| commerce-module/.../CommerceCartRepository.java | CommerceCartRepository | DSLContext | A - keep removed |
| commerce-module/.../PurchaseOrderRepository.java | PurchaseOrderRepository | DSLContext | A - keep removed |
| delivery-module/.../DeliveryDestinationUriIndexService.java | DeliveryDestinationUriIndexService | DSLContext | A - keep removed |
| delivery-module/.../DeliveryRemoteUriIndexService.java | DeliveryRemoteUriIndexService | DSLContext | A - keep removed |
| delivery-module/.../DeliveryStorageUriReferenceContributor.java | DeliveryStorageUriReferenceContributor | DSLContext | A - keep removed |
| entitlement-module/.../CustomPolicyRepository.java | CustomPolicyRepository | DSLContext | A - keep removed |
| entitlement-module/.../EntitlementBundleRepository.java | EntitlementBundleRepository | DSLContext | A - keep removed |
| entitlement-module/.../EntitlementGrantRepository.java | EntitlementGrantRepository | DSLContext | A - keep removed |
| entitlement-module/.../EntitlementOverrideRepository.java | EntitlementOverrideRepository | DSLContext | A - keep removed |
| entitlement-module/.../QuotaProfileRepository.java | QuotaProfileRepository | DSLContext | A - keep removed |
| entitlement-module/.../QuotaUsageJdbcRepository.java | QuotaUsageJdbcRepository | DSLContext | A - keep removed |
| entitlement-module/.../TenantTierJdbcRepository.java | TenantTierJdbcRepository | DSLContext | A - keep removed |
| entitlement-module/.../WorkspaceEntitlementPoolRepository.java | WorkspaceEntitlementPoolRepository | DSLContext | A - keep removed |
| entitlement-module/.../WorkspaceMemberEntitlementGrantRepository.java | WorkspaceMemberEntitlementGrantRepository | DSLContext | A - keep removed |
| entitlement-module/.../WorkspaceQuotaAllocationRepository.java | WorkspaceQuotaAllocationRepository | DSLContext | A - keep removed |
| federation-query-module/.../NlqJdbcRepository.java | NlqJdbcRepository | DSLContext | A - keep removed |
| payment-module/.../PaymentAttemptRepository.java | PaymentAttemptRepository | DSLContext | A - keep removed |
| payment-module/.../ProviderWebhookEventRepository.java | ProviderWebhookEventRepository | DSLContext | A - keep removed |
| platform-app/.../web/collaboration/SharedResourceJdbcRepository.java | SharedResourceJdbcRepository | DSLContext | A - keep removed |

#### A2. JdbcTemplate-dependent repositories (all removed, should stay removed):

| File | Class | Dependency | Verdict |
|------|-------|-----------|---------|
| billing-module/.../BillingPersistenceBootstrap.java | BillingPersistenceBootstrap | JdbcTemplate | A - keep removed |
| billing-module/.../SubscriptionJdbcRepository.java | SubscriptionJdbcRepository | JdbcTemplate | A - keep removed |
| entitlement-module/.../EntitlementPersistenceBootstrap.java | EntitlementPersistenceBootstrap | JdbcTemplate | A - keep removed |
| entitlement-module/.../EntitlementTierPersistenceBootstrap.java | EntitlementTierPersistenceBootstrap | JdbcTemplate | A - keep removed |
| entitlement-module/.../QuotaUsagePersistenceBootstrap.java | QuotaUsagePersistenceBootstrap | JdbcTemplate | A - keep removed |
| entitlement-module/.../TenantTierJdbcRepository.java | TenantTierJdbcRepository | JdbcTemplate | A - keep removed |
| entitlement-module/.../QuotaUsageJdbcRepository.java | QuotaUsageJdbcRepository | JdbcTemplate | A - keep removed |
| federation-query-module/.../NlqPersistenceBootstrap.java | NlqPersistenceBootstrap | JdbcTemplate | A - keep removed |
| policy-governance-module/.../FeatureFlagJdbcStore.java | FeatureFlagJdbcStore | JdbcTemplate | A - keep removed |
| policy-governance-module/.../FeatureFlagStartupHydrator.java | FeatureFlagStartupHydrator | FeatureFlagJdbcStore | A - keep removed |
| prompt-module/.../PromptJdbcRepository.java | PromptJdbcRepository | JdbcTemplate | A - keep removed |
| prompt-module/.../PromptPersistenceBootstrap.java | PromptPersistenceBootstrap | JdbcTemplate | A - keep removed |
| user-analytics-module/.../JdbcUserProfileRepository.java | JdbcUserProfileRepository | JdbcTemplate | A - keep removed |
| user-analytics-module/.../JdbcUserHabitsRepository.java | JdbcUserHabitsRepository | JdbcTemplate | A - keep removed |
| user-analytics-module/.../JdbcUserSegmentRepository.java | JdbcUserSegmentRepository | JdbcTemplate | A - keep removed |
| user-analytics-module/.../JdbcUserBehaviorEventRepository.java | JdbcUserBehaviorEventRepository | JdbcTemplate | A - keep removed |

### Category B: Should be replaced with @ConditionalOnProperty

These are optional infrastructure beans that should be gated by feature flags.

| File | Class | Dependency | Verdict |
|------|-------|-----------|---------|
| storage-module/.../S3AssetDownloadUrlPort.java | S3AssetDownloadUrlPort | BlobStorage | B - add @ConditionalOnProperty for storage.s3 |
| platform-app/.../app/ai/SpringAiOpenAiChatProvider.java | SpringAiOpenAiChatProvider | ChatClient.Builder | B - add @ConditionalOnProperty for ai.openai |
| platform-app/.../app/ai/TenantAwareLitellmChatProvider.java | TenantAwareLitellmChatProvider | ChatClient.Builder | B - add @ConditionalOnProperty for ai.litellm |
| platform-app/.../lifecycle/TemporalWorkerHealthIndicator.java | TemporalWorkerHealthIndicator | WorkerFactory | B - add @ConditionalOnProperty for temporal |
| platform-app/.../production/PlatformReadinessHealthIndicator.java | PlatformReadinessHealthIndicator | DataSource | B - add @ConditionalOnProperty for platform.readiness |
| workflow-module/.../TemporalWorkerGracefulShutdown.java | TemporalWorkerGracefulShutdown | WorkerFactory | B - add @ConditionalOnProperty for temporal |
| secrets-config-module/.../VaultKv2SecretProvider.java | VaultKv2SecretProvider | VaultTemplate | B - add @ConditionalOnProperty for secrets.vault |
| policy-governance-module/.../FeatureFlagStartupHydrator.java | FeatureFlagStartupHydrator | FeatureFlagJdbcStore | B - handled by FeatureFlagJdbcStore |

### Category C: DataSourceConfiguration DSLContext bean

The `DataSourceConfiguration.dslContext()` bean had `@ConditionalOnMissingBean(DSLContext.class)`
which is correct behavior. The issue was it hardcoded `SQLDialect.H2`. Fixed to auto-detect.

### Summary

- Category A (keep removed): 40+ repository classes - these are core data access
  beans that should be unconditional. Their dependencies (DSLContext, JdbcTemplate)
  are always present when the module is included in the application context.
- Category B (add @ConditionalOnProperty): 8 optional infrastructure beans that
  should be gated by feature flags.
- Category C (fix): 1 bean with correct condition but wrong H2 hardcoding.
