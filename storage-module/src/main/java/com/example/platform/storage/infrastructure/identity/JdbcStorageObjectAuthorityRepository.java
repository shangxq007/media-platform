package com.example.platform.storage.infrastructure.identity;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.StorageObjectIssuance.PlacementReceipt;
import com.example.platform.storage.api.StorageObjectIssuance.ReceiptPurpose;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence for the canonical Storage logical-object authority. */
@Repository
public class JdbcStorageObjectAuthorityRepository implements StorageObjectAuthorityRepository {

    private final JdbcTemplate jdbc;

    public JdbcStorageObjectAuthorityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IssuanceResult> findOriginalIssuance(
            StorageOwnershipScope owner, IssuanceIdempotencyKey idempotencyKey) {
        List<IssuanceResult> results = jdbc.query("""
                select o.object_id, o.tenant_id, o.project_id,
                       o.issuance_idempotency_key, o.semantic_fingerprint,
                       r.replica_id, r.provider_id, r.namespace_tenant_id,
                       r.namespace_project_id, r.namespace_class, r.region_policy,
                       r.data_classification, r.opaque_locator, r.provider_version_token,
                       r.region, r.placement_state, r.committed_digest_algorithm,
                       r.committed_digest, r.committed_length, r.provider_correlation_id,
                       r.receipt_id, r.issued_at
                  from storage_logical_object o
                  join storage_placement_receipt r
                    on r.object_id = o.object_id
                   and r.receipt_purpose = 'ORIGINAL_ISSUANCE'
                 where o.tenant_id = ?
                   and o.project_id is not distinct from ?
                   and o.issuance_idempotency_key = ?
                """, (rs, rowNumber) -> {
            StorageOwnershipScope resolvedOwner = new StorageOwnershipScope(
                    rs.getString("tenant_id"), rs.getString("project_id"));
            StorageObjectId objectId = new StorageObjectId(rs.getString("object_id"));
            StorageReplicaId replicaId = new StorageReplicaId(rs.getString("replica_id"));
            StorageObjectLocation location = new StorageObjectLocation(
                    new StorageProviderId(rs.getString("provider_id")),
                    new StorageNamespace(
                            rs.getString("namespace_tenant_id"),
                            rs.getString("namespace_project_id"),
                            NamespaceClass.valueOf(rs.getString("namespace_class")),
                            RegionPolicy.valueOf(rs.getString("region_policy")),
                            DataClassification.valueOf(rs.getString("data_classification"))),
                    rs.getString("opaque_locator"),
                    rs.getString("provider_version_token"),
                    rs.getString("region"));
            ContentDigest digest = new ContentDigest(
                    ContentDigest.DigestAlgorithm.valueOf(rs.getString("committed_digest_algorithm")),
                    rs.getString("committed_digest"));
            long length = rs.getLong("committed_length");
            String correlationId = rs.getString("provider_correlation_id");
            String fingerprint = rs.getString("semantic_fingerprint");
            BackendPlacementResult placement = new BackendPlacementResult(
                    replicaId,
                    location,
                    ReplicaState.valueOf(rs.getString("placement_state")),
                    digest,
                    length,
                    correlationId);
            PlacementReceipt receipt = new PlacementReceipt(
                    rs.getString("receipt_id"),
                    new IssuanceIdempotencyKey(rs.getString("issuance_idempotency_key")),
                    fingerprint,
                    ReceiptPurpose.ORIGINAL_ISSUANCE,
                    objectId,
                    replicaId,
                    location,
                    ReplicaState.valueOf(rs.getString("placement_state")),
                    digest,
                    length,
                    correlationId,
                    rs.getObject("issued_at", OffsetDateTime.class).toInstant());
            return new IssuanceResult(resolvedOwner, objectId, placement, receipt);
        }, owner.tenantId(), owner.projectId(), idempotencyKey.value());
        if (results.size() > 1) {
            throw new IllegalStateException("one issuance key resolved to multiple placement receipts");
        }
        return results.stream().findFirst();
    }

    @Override
    public void saveInitialPlacementAndReceipt(IssuanceResult result) {
        PlacementReceipt receipt = result.receipt();
        BackendPlacementResult placement = result.placement();
        StorageObjectLocation location = placement.location();
        StorageNamespace namespace = location.namespace();
        OffsetDateTime issuedAt = OffsetDateTime.ofInstant(receipt.issuedAt(), ZoneOffset.UTC);

        jdbc.update("""
                insert into storage_object_placement (
                    replica_id, object_id, provider_id, namespace_tenant_id,
                    namespace_project_id, namespace_class, region_policy,
                    data_classification, opaque_locator, provider_version_token,
                    region, placement_state, committed_digest_algorithm,
                    committed_digest, committed_length, provider_correlation_id, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                placement.replicaId().value(), result.objectId().value(),
                location.providerId().value(), namespace.tenantId(), namespace.projectId(),
                namespace.namespaceClass().name(), namespace.regionPolicy().name(),
                namespace.dataClassification().name(), location.opaqueLocator(),
                location.providerVersionToken(), location.region(), placement.state().name(),
                placement.committedDigest().algorithm().name(),
                placement.committedDigest().canonicalValue(), placement.committedLength(),
                placement.providerCorrelationId(), issuedAt);

        jdbc.update("""
                insert into storage_placement_receipt (
                    receipt_id, idempotency_key, semantic_fingerprint, receipt_purpose,
                    object_id, replica_id,
                    provider_id, namespace_tenant_id, namespace_project_id, namespace_class,
                    region_policy, data_classification, opaque_locator, provider_version_token,
                    placement_state,
                    region, committed_digest_algorithm, committed_digest, committed_length,
                    provider_correlation_id, issued_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receipt.receiptId(), receipt.idempotencyKey().value(),
                receipt.semanticFingerprint(), receipt.purpose().name(),
                receipt.objectId().value(), receipt.replicaId().value(),
                receipt.location().providerId().value(), receipt.location().namespace().tenantId(),
                receipt.location().namespace().projectId(),
                receipt.location().namespace().namespaceClass().name(),
                receipt.location().namespace().regionPolicy().name(),
                receipt.location().namespace().dataClassification().name(),
                receipt.location().opaqueLocator(), receipt.location().providerVersionToken(),
                receipt.state().name(), receipt.location().region(),
                receipt.committedDigest().algorithm().name(),
                receipt.committedDigest().canonicalValue(), receipt.committedLength(),
                receipt.providerCorrelationId(), issuedAt);
    }
}
