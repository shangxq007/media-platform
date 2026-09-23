package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.PlatformExecutionPlan;
import java.util.*;
import org.springframework.stereotype.Service;
import com.example.platform.composition.app.CompositionAdmissionRepository.AdmissionRecord;

/** Durable Composition admission facade over the one platform admission contract. */
@Service
public final class CompositionAdmissionService implements PlatformExecutionAdmissionPort {
    private final CompositionAdmissionRepository repository;
    private final CompositionQuotaChargePort quota;
    private final CompositionResultRepository results;

    public CompositionAdmissionService(CompositionAdmissionRepository repository,
            CompositionQuotaChargePort quota, CompositionResultRepository results) {
        this.repository = Objects.requireNonNull(repository);
        this.quota = Objects.requireNonNull(quota);
        this.results = Objects.requireNonNull(results);
    }

    public AdmissionDecision admit(TemplateWorkflow workflow, ProviderRegistryBoundary registry,
            Set<String> assets, Set<String> entitlements, PlatformExecutionPlan.Scope scope,
            ExecutionPlanId planId, String idempotencyKey, String requestHash,
            String quotaSnapshotId, long quotaUnits, Map<String, String> entitlementSnapshot,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {
        PlatformExecutionPlan plan = CompositionPlatformExecutionPlanAdapter.adapt(workflow, registry,
                assets, entitlements, scope, planId, idempotencyKey, requestHash,
                quotaSnapshotId, quotaUnits, entitlementSnapshot, audit);
        AdmissionDecision existing = repository.find(scope.tenantId(), scope.workspaceId(), idempotencyKey)
                .map(r -> new AdmissionDecision(r.executionId(), r.ownershipGeneration(), false)).orElse(null);
        if (existing != null) return existing;
        AdmissionRecord record = repository.admit(plan, workflow.id(), workflow.revision());
        if (!repository.claimQuotaCharge(record.executionId())) return new AdmissionDecision(record.executionId(), record.ownershipGeneration(), false);
        try { quota.charge(plan, record.executionId()); } catch (RuntimeException failure) { repository.releaseQuotaCharge(record.executionId()); throw failure; }
        return new AdmissionDecision(record.executionId(), record.ownershipGeneration(), true);
    }

    @Override public AdmissionDecision admit(PlatformExecutionPlan plan) {
        AdmissionRecord record = repository.admit(plan, plan.source().sourceId(), revision(plan.source().revision()));
        boolean charged = repository.claimQuotaCharge(record.executionId());
        if (charged) try { quota.charge(plan, record.executionId()); } catch (RuntimeException failure) { repository.releaseQuotaCharge(record.executionId()); throw failure; }
        return new AdmissionDecision(record.executionId(), record.ownershipGeneration(), charged);
    }
    @Override public boolean cancel(String executionId, long generation) { return repository.transition(executionId, generation, "ADMITTED", "CANCELLED"); }
    @Override public boolean retry(String executionId, long generation) { return repository.transition(executionId, generation, "FAILED", "RETRYING"); }
    @Override public Optional<com.example.platform.execution.result.PlatformCompletionReference> completed(String tenant, String key, String hash) { return results.findByIdempotency(tenant, key, hash); }
    private static long revision(String value) { try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; } }
}
