package com.example.platform.storage.app.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceCommand;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StorageWriteIntentRecovery.BeginWriteIntentCommand;
import com.example.platform.storage.api.StorageWriteIntentRecovery.CompleteWriteIntentCommand;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.storage.domain.identity.StorageWriteIntent.State;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalStorageObjectIssuanceServiceTest {

    private static final String FINGERPRINT = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private final InMemoryStore store = new InMemoryStore();
    private final CanonicalStorageWriteIntentRecoveryService recovery =
            new CanonicalStorageWriteIntentRecoveryService(
                    new CanonicalStorageObjectIdAllocator(), store, store,
                    Clock.fixed(NOW, ZoneOffset.UTC));
    private final CanonicalStorageObjectIssuanceService service =
            new CanonicalStorageObjectIssuanceService(recovery);

    @Test
    void sameFullOwnerKeyAndInputReturnsSameObjectAndOriginalReceipt() {
        IssuanceCommand command = command(owner("tenant-1", "project-1"), "issue-1",
                placement("replica-1", "namespace-is-not-owner"));
        IssuanceResult first = service.issue(command);
        IssuanceResult replay = service.issue(command);

        assertEquals(first, replay);
        assertEquals(command.owner(), first.owner());
        assertTrue(first.objectId().value().startsWith("so-"));
        assertNotEquals(first.placement().location().opaqueLocator(), first.objectId().value());
        assertNotEquals(first.owner().tenantId(),
                first.placement().location().namespace().tenantId());
    }

    @Test
    void fullOwnerScopeSeparatesProjectsTenantOnlyAndDifferentTenants() {
        IssuanceResult projectA = service.issue(command(
                owner("tenant-a", "project-a"), "shared-key",
                placement("replica-project-a", "placement-tenant")));
        IssuanceResult projectB = service.issue(command(
                owner("tenant-a", "project-b"), "shared-key",
                placement("replica-project-b", "placement-tenant")));
        IssuanceResult tenantOnly = service.issue(command(
                StorageOwnershipScope.tenant("tenant-a"), "shared-key",
                placement("replica-tenant-only", "placement-tenant")));
        IssuanceResult otherTenant = service.issue(command(
                owner("tenant-b", "project-a"), "shared-key",
                placement("replica-other-tenant", "placement-tenant")));

        assertNotEquals(projectA.objectId(), projectB.objectId());
        assertNotEquals(projectA.objectId(), tenantOnly.objectId());
        assertNotEquals(projectB.objectId(), tenantOnly.objectId());
        assertNotEquals(projectA.objectId(), otherTenant.objectId());
        assertNull(tenantOnly.owner().projectId());
    }

    @Test
    void sameFullOwnerKeyWithDifferentSemanticInputFailsClosed() {
        IssuanceCommand first = command(owner("tenant-1", "project-1"), "issue-2",
                placement("replica-2", "tenant-1"));
        service.issue(first);
        assertThrows(StorageIssuanceConflictException.class,
                () -> recovery.beginOrResume(new BeginWriteIntentCommand(
                        first.owner(), first.idempotencyKey(), "b".repeat(64), null)));
    }

    @Test
    void sameFullOwnerKeyWithDifferentProviderInputFailsClosed() {
        StorageOwnershipScope owner = owner("tenant-placement", "project-placement");
        service.issue(command(owner, "placement-key",
                placement("replica-placement-a", "namespace-a")));

        assertThrows(StorageIssuanceConflictException.class,
                () -> service.issue(command(owner, "placement-key",
                        placement("replica-placement-b", "namespace-b"))));
    }

    @Test
    void providerCompletionSurvivesCanonicalFailureAndRetryAllocatesNoSecondId() {
        BeginWriteIntentCommand begin = new BeginWriteIntentCommand(
                owner("tenant-recovery", null), new IssuanceIdempotencyKey("recover-1"),
                FINGERPRINT, "provider-request-1");
        StorageWriteIntent intent = recovery.beginOrResume(begin);
        BackendPlacementResult placement = placement(
                "replica-recover", "placement-tenant", "correlation-recover");
        StorageWriteIntent providerCompleted =
                recovery.recordProviderCompleted(intent.writeIntentId(), placement);
        assertEquals(providerCompleted,
                recovery.recordProviderCompleted(intent.writeIntentId(), placement));
        store.failNextCanonicalSave = true;
        assertThrows(IllegalStateException.class,
                () -> recovery.complete(new CompleteWriteIntentCommand(
                        intent.writeIntentId(), placement)));
        StorageWriteIntent afterFailure = store.findById(intent.writeIntentId()).orElseThrow();
        assertEquals(State.PROVIDER_COMPLETED, afterFailure.state());
        assertEquals(intent.objectId(), afterFailure.objectId());
        BackendPlacementResult changedCompletion = new BackendPlacementResult(
                placement.replicaId(), placement.location(), placement.state(),
                placement.committedDigest(), 43, placement.providerCorrelationId());
        assertThrows(StorageIssuanceConflictException.class,
                () -> recovery.complete(new CompleteWriteIntentCommand(
                        intent.writeIntentId(), changedCompletion)));

        StorageWriteIntent resumed = recovery.beginOrResume(begin);
        assertEquals(intent.objectId(), resumed.objectId());
        IssuanceResult completed = recovery.complete(new CompleteWriteIntentCommand(
                intent.writeIntentId(), placement));
        assertEquals(intent.objectId(), completed.objectId());
        assertSame(completed, recovery.complete(new CompleteWriteIntentCommand(
                intent.writeIntentId(), placement)));
    }

    private static IssuanceCommand command(
            StorageOwnershipScope owner, String key, BackendPlacementResult placement) {
        return new IssuanceCommand(
                owner, new IssuanceIdempotencyKey(key), FINGERPRINT, placement);
    }

    private static StorageOwnershipScope owner(String tenant, String project) {
        return new StorageOwnershipScope(tenant, project);
    }

    private static BackendPlacementResult placement(String replicaId, String namespaceTenant) {
        return placement(replicaId, namespaceTenant, "provider-correlation-" + replicaId);
    }

    private static BackendPlacementResult placement(
            String replicaId, String namespaceTenant, String correlationId) {
        StorageNamespace namespace = new StorageNamespace(
                namespaceTenant, null, NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        return new BackendPlacementResult(
                new StorageReplicaId(replicaId),
                new StorageObjectLocation(
                        new StorageProviderId("provider-1"), namespace,
                        "opaque/backend/" + replicaId, "version-1", "region-1"),
                ReplicaState.AVAILABLE,
                ContentDigest.sha256("0".repeat(64)),
                42,
                correlationId);
    }

    private static final class InMemoryStore
            implements StorageWriteIntentRepository, StorageObjectAuthorityRepository {
        private final Map<String, StorageWriteIntent> intentsById = new HashMap<>();
        private final Map<String, StorageWriteIntent> intentsByOwnerKey = new HashMap<>();
        private final Map<String, IssuanceResult> issuances = new HashMap<>();
        private boolean failNextCanonicalSave;

        @Override
        public void lockOwnerKey(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {}

        @Override
        public void lockWriteIntent(String writeIntentId) {}

        @Override
        public Optional<StorageWriteIntent> findByOwnerKey(
                StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
            return Optional.ofNullable(intentsByOwnerKey.get(mapKey(owner, key)));
        }

        @Override
        public Optional<StorageWriteIntent> findById(String writeIntentId) {
            return Optional.ofNullable(intentsById.get(writeIntentId));
        }

        @Override
        public void create(StorageWriteIntent intent) {
            intentsById.put(intent.writeIntentId(), intent);
            intentsByOwnerKey.put(mapKey(intent.owner(), intent.idempotencyKey()), intent);
        }

        @Override
        public StorageWriteIntent recordProviderCompleted(
                String writeIntentId, String correlation,
                String completionFingerprint, Instant updatedAt) {
            StorageWriteIntent old = intentsById.get(writeIntentId);
            if (old.state() == State.CANONICAL_COMMITTED
                    || old.state() == State.PROVIDER_COMPLETED) {
                if (!correlation.equals(old.providerCorrelationId())
                        || !completionFingerprint.equals(
                                old.providerCompletionFingerprint())) {
                    throw new StorageIssuanceConflictException("different correlation");
                }
                return old;
            }
            return replace(old, State.PROVIDER_COMPLETED, correlation,
                    completionFingerprint, updatedAt, null);
        }

        @Override
        public StorageWriteIntent recordCanonicalCommitted(
                String writeIntentId, Instant completedAt) {
            StorageWriteIntent old = intentsById.get(writeIntentId);
            return replace(old, State.CANONICAL_COMMITTED,
                    old.providerCorrelationId(), old.providerCompletionFingerprint(),
                    completedAt, completedAt);
        }

        @Override
        public Optional<IssuanceResult> findOriginalIssuance(
                StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
            return Optional.ofNullable(issuances.get(mapKey(owner, key)));
        }

        @Override
        public void saveInitialPlacementAndReceipt(IssuanceResult result) {
            if (failNextCanonicalSave) {
                failNextCanonicalSave = false;
                throw new IllegalStateException("simulated receipt persistence failure");
            }
            issuances.put(mapKey(result.owner(), result.receipt().idempotencyKey()), result);
        }

        private StorageWriteIntent replace(
                StorageWriteIntent old, State state, String correlation,
                String completionFingerprint, Instant updatedAt, Instant completedAt) {
            StorageWriteIntent replacement = new StorageWriteIntent(
                    old.writeIntentId(), old.objectId(), old.owner(), old.idempotencyKey(),
                    old.semanticFingerprint(), old.providerRequestId(), correlation,
                    completionFingerprint, state,
                    old.lastErrorCode(), old.reconciliationDetail(), old.createdAt(),
                    updatedAt, completedAt);
            intentsById.put(replacement.writeIntentId(), replacement);
            intentsByOwnerKey.put(
                    mapKey(replacement.owner(), replacement.idempotencyKey()), replacement);
            return replacement;
        }

        private static String mapKey(
                StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
            String project = owner.projectId();
            String projectComponent = project == null
                    ? "project:null"
                    : "project:value:" + project.length() + ":" + project;
            return "tenant:value:" + owner.tenantId().length() + ":" + owner.tenantId()
                    + "\u0000" + projectComponent
                    + "\u0000key:value:" + key.value().length() + ":" + key.value();
        }
    }
}
