package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import com.example.platform.execution.admission.ProviderBoundExecutionPlan;

/** Server-owned admission-time entitlement and quota decision. */
public interface CompositionEntitlementQuotaResolver {
    ProviderBoundExecutionPlan.EntitlementQuotaSnapshot resolve(TemplateWorkflow workflow,
            String tenantId, String workspaceId, String actorId);
}
