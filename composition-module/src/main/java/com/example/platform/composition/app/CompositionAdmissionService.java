package com.example.platform.composition.app;

import com.example.platform.execution.admission.PlatformExecutionAdmissionPort;
import com.example.platform.execution.planning.PlatformExecutionPlan;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.springframework.stereotype.Service;
import com.example.platform.composition.app.CompositionAdmissionRepository.AdmissionRecord;

/** Durable Composition admission facade over the one platform admission contract. */
@Service
public class CompositionAdmissionService implements PlatformExecutionAdmissionPort {
    private final CompositionAdmissionRepository repository;
    private final CompositionQuotaChargePort quota;
    private final CompositionResultRepository results;
    private final CompositionAccess access;
    private final CompositionPublishedRevisionAuthority revisions;
    private final CompositionProviderBoundCapabilityAuthority capabilities;
    private final CompositionResourceResolver resources;
    private final CompositionEntitlementQuotaResolver entitlements;

    public CompositionAdmissionService(CompositionAdmissionRepository repository,
            CompositionQuotaChargePort quota, CompositionResultRepository results,
            CompositionAccess access, CompositionPublishedRevisionAuthority revisions,
            CompositionProviderBoundCapabilityAuthority capabilities,
            CompositionResourceResolver resources, CompositionEntitlementQuotaResolver entitlements) {
        this.repository = Objects.requireNonNull(repository);
        this.quota = Objects.requireNonNull(quota);
        this.results = Objects.requireNonNull(results);
        this.access = Objects.requireNonNull(access); this.revisions = Objects.requireNonNull(revisions);
        this.capabilities = Objects.requireNonNull(capabilities); this.resources = Objects.requireNonNull(resources);
        this.entitlements = Objects.requireNonNull(entitlements);
    }

    /** Production Composition entry: only selectors and request intent cross this boundary. */
    @Transactional
    public AdmissionDecision admit(CompositionAdmissionRequest request) {
        var authenticated = access.resolve(request.workspaceSelection());
        var scope = new ProviderBoundExecutionPlan.Scope(authenticated.tenantId(), authenticated.workspaceId(), authenticated.actorId());
        ProviderBoundExecutionPlan plan = CompositionProviderBoundExecutionPlanAdapter.lower(revisions, capabilities, resources, entitlements, scope,
                request.compositionId(), request.publishedVersion(), request.idempotencyKey(), request.requestHash(),
                request.cancellationPolicy(), request.retryPolicy(), request.parameters());
        return admit(plan);
    }

    @Override @Transactional
    public AdmissionDecision admit(ProviderBoundExecutionPlan plan) {
        var existing = repository.find(plan.scope().tenantId(), plan.scope().workspaceId(), plan.idempotency().key());
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.requestHash().equals(plan.idempotency().requestHash())
                    || !record.planFingerprint().equals(plan.planFingerprint().value()))
                throw new IllegalArgumentException("idempotency key conflicts with an accepted Composition request");
            return decision(record, false);
        }
        var record = repository.admit(plan);
        if (!repository.claimQuotaCharge(record.executionId())) return decision(repository.findByExecutionId(record.executionId()).orElseThrow(), false);
        try {
            quota.charge(plan, record.executionId());
            repository.markQuotaCharged(record.executionId(), plan.entitlementQuota().quotaUnits());
            return decision(repository.findByExecutionId(record.executionId()).orElseThrow(), true);
        } catch (RuntimeException failure) {
            repository.deleteUncharged(record.executionId());
            throw failure;
        }
    }

    @Override public AdmissionDecision admit(PlatformExecutionPlan plan) { throw new IllegalArgumentException("caller-constructed Composition plans are not accepted"); }
    @Override public boolean cancel(String executionId, long generation) { return repository.transition(executionId, generation, "ADMITTED", "CANCELLED"); }
    @Override public boolean retry(String executionId, long generation) { return repository.transition(executionId, generation, "FAILED", "RETRYING"); }
    @Override public Optional<com.example.platform.execution.result.PlatformCompletionReference> completed(String tenant, String key, String hash) { return results.findByIdempotency(tenant, key, hash); }
    private static AdmissionDecision decision(AdmissionRecord record, boolean newlyAdmitted) {
        return new AdmissionDecision(record.executionId(), record.ownershipGeneration(), newlyAdmitted,
                record.state(), record.quotaCharged(), record.plan());
    }
}
