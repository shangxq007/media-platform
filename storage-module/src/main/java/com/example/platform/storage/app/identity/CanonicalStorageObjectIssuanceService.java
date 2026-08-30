package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical Storage application authority for logical object issuance. */
@Service
public class CanonicalStorageObjectIssuanceService implements StorageObjectIssuance {

    private final CanonicalStorageObjectIdAllocator allocator;
    private final StorageObjectAuthorityRepository repository;
    private final Clock clock;

    public CanonicalStorageObjectIssuanceService(
            CanonicalStorageObjectIdAllocator allocator,
            StorageObjectAuthorityRepository repository,
            Clock clock) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public IssuanceResult issue(IssuanceCommand command) {
        Objects.requireNonNull(command, "command");
        repository.lockIdempotencyKey(command.idempotencyKey());

        return repository.findByIdempotencyKey(command.idempotencyKey())
                .map(existing -> replayOrFail(command, existing))
                .orElseGet(() -> create(command));
    }

    private IssuanceResult create(IssuanceCommand command) {
        StorageObjectId objectId = allocator.allocate();
        Instant issuedAt = clock.instant();
        PlacementReceipt receipt = new PlacementReceipt(
                "spr-" + java.util.UUID.randomUUID(),
                command.idempotencyKey(),
                command.semanticFingerprint(),
                objectId,
                command.backendPlacement().replicaId(),
                command.backendPlacement().location(),
                command.backendPlacement().committedDigest(),
                command.backendPlacement().committedLength(),
                command.backendPlacement().providerCorrelationId(),
                issuedAt);
        IssuanceResult created = new IssuanceResult(objectId, command.backendPlacement(), receipt);
        repository.save(created);
        return created;
    }

    private static IssuanceResult replayOrFail(IssuanceCommand command, IssuanceResult existing) {
        if (!existing.receipt().semanticFingerprint().equals(command.semanticFingerprint())
                || !existing.placement().equals(command.backendPlacement())) {
            throw new StorageIssuanceConflictException(
                    "issuance idempotency key was already used with different input");
        }
        return existing;
    }
}
