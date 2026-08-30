package com.example.platform.storage.app.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceCommand;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalStorageObjectIssuanceServiceTest {

    private static final String FINGERPRINT = "a".repeat(64);
    private final InMemoryAuthorityRepository repository = new InMemoryAuthorityRepository();
    private final CanonicalStorageObjectIssuanceService service =
            new CanonicalStorageObjectIssuanceService(
                    new CanonicalStorageObjectIdAllocator(), repository,
                    Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void sameKeyAndInputReturnsSameObjectAndReceipt() {
        IssuanceCommand command = new IssuanceCommand("issue-1", FINGERPRINT, placement("replica-1"));
        IssuanceResult first = service.issue(command);
        IssuanceResult replay = service.issue(command);

        assertEquals(first, replay);
        assertTrue(first.objectId().value().startsWith("so-"));
        assertNotEquals(first.placement().location().opaqueLocator(), first.objectId().value());
    }

    @Test
    void sameKeyWithDifferentInputFailsClosed() {
        service.issue(new IssuanceCommand("issue-2", FINGERPRINT, placement("replica-2")));
        assertThrows(StorageIssuanceConflictException.class,
                () -> service.issue(new IssuanceCommand(
                        "issue-2", "b".repeat(64), placement("replica-other"))));
    }

    private static BackendPlacementResult placement(String replicaId) {
        StorageNamespace namespace = new StorageNamespace(
                "tenant-1", "project-1", NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        return new BackendPlacementResult(
                new StorageReplicaId(replicaId),
                new StorageObjectLocation(
                        new StorageProviderId("provider-1"), namespace,
                        "opaque/backend/location", "version-1", "region-1"),
                ReplicaState.AVAILABLE,
                ContentDigest.sha256("0".repeat(64)),
                42,
                "provider-correlation-" + replicaId);
    }

    private static final class InMemoryAuthorityRepository
            implements StorageObjectAuthorityRepository {
        private final Map<String, IssuanceResult> values = new HashMap<>();

        @Override
        public void lockIdempotencyKey(String idempotencyKey) {}

        @Override
        public Optional<IssuanceResult> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(values.get(idempotencyKey));
        }

        @Override
        public void save(IssuanceResult result) {
            values.put(result.receipt().idempotencyKey(), result);
        }
    }
}
