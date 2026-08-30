package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.PlacementReceipt;
import com.example.platform.storage.api.StorageObjectIssuance.ReceiptPurpose;
import com.example.platform.storage.api.StorageWriteIntentRecovery;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.domain.identity.StableStorageFingerprint;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.storage.domain.identity.StorageWriteIntent.State;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical durable recovery authority for normal Storage writes. */
@Service
public class CanonicalStorageWriteIntentRecoveryService implements StorageWriteIntentRecovery {

    private final CanonicalStorageObjectIdAllocator allocator;
    private final StorageWriteIntentRepository intentRepository;
    private final StorageObjectAuthorityRepository objectRepository;
    private final Clock clock;

    public CanonicalStorageWriteIntentRecoveryService(
            CanonicalStorageObjectIdAllocator allocator,
            StorageWriteIntentRepository intentRepository,
            StorageObjectAuthorityRepository objectRepository,
            Clock clock) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.intentRepository = Objects.requireNonNull(intentRepository, "intentRepository");
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public StorageWriteIntent beginOrResume(BeginWriteIntentCommand command) {
        Objects.requireNonNull(command, "command");
        intentRepository.lockOwnerKey(command.owner(), command.idempotencyKey());
        return intentRepository.findByOwnerKey(command.owner(), command.idempotencyKey())
                .map(existing -> replayOrFail(command, existing))
                .orElseGet(() -> create(command));
    }

    @Override
    @Transactional
    public StorageWriteIntent recordProviderCompleted(
            String writeIntentId, BackendPlacementResult backendPlacement) {
        requireText(writeIntentId, "writeIntentId");
        Objects.requireNonNull(backendPlacement, "backendPlacement");
        intentRepository.lockWriteIntent(writeIntentId);
        return intentRepository.recordProviderCompleted(
                writeIntentId, backendPlacement.providerCorrelationId(),
                providerCompletionFingerprint(backendPlacement), clock.instant());
    }

    @Override
    @Transactional
    public IssuanceResult complete(CompleteWriteIntentCommand command) {
        Objects.requireNonNull(command, "command");
        intentRepository.lockWriteIntent(command.writeIntentId());
        StorageWriteIntent intent = intentRepository.findById(command.writeIntentId())
                .orElseThrow(() -> new IllegalArgumentException("unknown storage write intent"));
        if (intent.state() == State.CANONICAL_COMMITTED) {
            IssuanceResult existing = objectRepository.findOriginalIssuance(
                            intent.owner(), intent.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "committed storage write intent has no original issuance receipt"));
            return replayCompletionOrFail(command, existing);
        }
        if (intent.state() != State.PROVIDER_COMPLETED) {
            throw new IllegalStateException(
                    "storage write intent requires durable provider completion first");
        }
        if (!Objects.equals(
                intent.providerCorrelationId(),
                command.backendPlacement().providerCorrelationId())) {
            throw new StorageIssuanceConflictException(
                    "provider completion correlation does not match the write intent");
        }
        if (!Objects.equals(
                intent.providerCompletionFingerprint(),
                providerCompletionFingerprint(command.backendPlacement()))) {
            throw new StorageIssuanceConflictException(
                    "provider completion facts do not match the durable write intent");
        }

        Instant issuedAt = clock.instant();
        PlacementReceipt receipt = new PlacementReceipt(
                "spr-" + UUID.randomUUID(),
                intent.idempotencyKey(),
                intent.semanticFingerprint(),
                ReceiptPurpose.ORIGINAL_ISSUANCE,
                intent.objectId(),
                command.backendPlacement().replicaId(),
                command.backendPlacement().location(),
                command.backendPlacement().state(),
                command.backendPlacement().committedDigest(),
                command.backendPlacement().committedLength(),
                command.backendPlacement().providerCorrelationId(),
                issuedAt);
        IssuanceResult created = new IssuanceResult(
                intent.owner(), intent.objectId(), command.backendPlacement(), receipt);
        objectRepository.saveInitialPlacementAndReceipt(created);
        intentRepository.recordCanonicalCommitted(intent.writeIntentId(), issuedAt);
        return created;
    }

    private StorageWriteIntent create(BeginWriteIntentCommand command) {
        StorageObjectId objectId = allocator.allocate();
        Instant createdAt = clock.instant();
        StorageWriteIntent intent = new StorageWriteIntent(
                "swi-" + UUID.randomUUID(),
                objectId,
                command.owner(),
                command.idempotencyKey(),
                command.semanticFingerprint(),
                command.providerRequestId(),
                null,
                null,
                State.PENDING_PROVIDER,
                null,
                null,
                createdAt,
                createdAt,
                null);
        intentRepository.create(intent);
        return intent;
    }

    private static StorageWriteIntent replayOrFail(
            BeginWriteIntentCommand command, StorageWriteIntent existing) {
        if (!existing.owner().equals(command.owner())
                || !existing.semanticFingerprint().equals(command.semanticFingerprint())
                || !Objects.equals(existing.providerRequestId(), command.providerRequestId())) {
            throw new StorageIssuanceConflictException(
                    "full-owner issuance key was already used with different semantic input");
        }
        return existing;
    }

    private static IssuanceResult replayCompletionOrFail(
            CompleteWriteIntentCommand command, IssuanceResult existing) {
        if (!existing.placement().equals(command.backendPlacement())) {
            throw new StorageIssuanceConflictException(
                    "completed write intent was replayed with different placement input");
        }
        return existing;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String providerCompletionFingerprint(BackendPlacementResult placement) {
        var location = placement.location();
        var namespace = location.namespace();
        return StableStorageFingerprint.sha256(List.of(
                "storage-provider-completion-v1",
                placement.replicaId().value(),
                location.providerId().value(),
                namespace.tenantId(),
                Objects.toString(namespace.projectId(), ""),
                namespace.namespaceClass().name(),
                namespace.regionPolicy().name(),
                namespace.dataClassification().name(),
                location.opaqueLocator(),
                Objects.toString(location.providerVersionToken(), ""),
                Objects.toString(location.region(), ""),
                placement.state().name(),
                placement.committedDigest().algorithm().name(),
                placement.committedDigest().canonicalValue(),
                Long.toString(placement.committedLength()),
                placement.providerCorrelationId()));
    }
}
