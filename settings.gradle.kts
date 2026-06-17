plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "media-platform"

include(
    "platform-app",
    "shared-kernel",
    "render-module",
    "notification-module",
    "ai-module",
    "spring-ai-adapter",
    "config-module",
    "workflow-module",
    "storage-module",
    "delivery-module",
    "prompt-module",
    "cloud-resource-module",
    "secrets-config-module",
    "extension-module",
    "datasource-module",
    "observability-module",
    "outbox-event-module",
    "audit-compliance-module",
    "scheduler-module",
    "identity-access-module",
    "quota-billing-module",
    "commerce-module",
    "payment-module",
    "billing-module",
    "entitlement-module",
    "policy-governance-module",
    "artifact-catalog-module",
    "sandbox-runtime-module",
    "sandbox-worker",
    "federation-query-module",
    "user-analytics-module",
    "compatibility-migration-module",
    "remote-render-worker",
    "social-publish-module",
    "product-layer-module"
)
