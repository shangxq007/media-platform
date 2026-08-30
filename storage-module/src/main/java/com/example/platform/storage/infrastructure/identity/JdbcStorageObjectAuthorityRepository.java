package com.example.platform.storage.infrastructure.identity;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.StorageObjectIssuance.PlacementReceipt;
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
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
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
    public void lockIdempotencyKey(String idempotencyKey) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, idempotencyKey);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public Optional<IssuanceResult> findByIdempotencyKey(String idempotencyKey) {
        List<IssuanceResult> results = jdbc.query("""
                select o.object_id, o.issuance_idempotency_key, o.semantic_fingerprint,
                       p.replica_id, p.provider_id, p.namespace_tenant_id,
                       p.namespace_project_id, p.namespace_class, p.region_policy,
                       p.data_classification, p.opaque_locator, p.provider_version_token,
                       p.region, p.placement_state, p.committed_digest_algorithm,
                       p.committed_digest, p.committed_length, p.provider_correlation_id,
                       r.receipt_id, r.issued_at
                  from storage_logical_object o
                  join storage_object_placement p on p.object_id = o.object_id
                  join storage_placement_receipt r
                    on r.object_id = p.object_id and r.replica_id = p.replica_id
                 where o.issuance_idempotency_key = ?
                """, (rs, rowNumber) -> {
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
                    rs.getString("issuance_idempotency_key"),
                    fingerprint,
                    objectId,
                    replicaId,
                    location,
                    digest,
                    length,
                    correlationId,
                    rs.getObject("issued_at", OffsetDateTime.class).toInstant());
            return new IssuanceResult(objectId, placement, receipt);
        }, idempotencyKey);
        if (results.size() > 1) {
            throw new IllegalStateException("one issuance key resolved to multiple placement receipts");
        }
        return results.stream().findFirst();
    }

    @Override
    public void save(IssuanceResult result) {
        PlacementReceipt receipt = result.receipt();
        BackendPlacementResult placement = result.placement();
        StorageObjectLocation location = placement.location();
        StorageNamespace namespace = location.namespace();
        OffsetDateTime issuedAt = OffsetDateTime.ofInstant(receipt.issuedAt(), ZoneOffset.UTC);

        jdbc.update("""
                insert into storage_logical_object (
                    object_id, issuance_idempotency_key, semantic_fingerprint, created_at
                ) values (?, ?, ?, ?)
                """,
                result.objectId().value(), receipt.idempotencyKey(),
                receipt.semanticFingerprint(), issuedAt);

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
                    receipt_id, idempotency_key, semantic_fingerprint, object_id, replica_id,
                    provider_id, namespace_tenant_id, namespace_project_id, namespace_class,
                    region_policy, data_classification, opaque_locator, provider_version_token,
                    region, committed_digest_algorithm, committed_digest, committed_length,
                    provider_correlation_id, issued_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receipt.receiptId(), receipt.idempotencyKey(), receipt.semanticFingerprint(),
                receipt.objectId().value(), receipt.replicaId().value(),
                receipt.location().providerId().value(), receipt.location().namespace().tenantId(),
                receipt.location().namespace().projectId(),
                receipt.location().namespace().namespaceClass().name(),
                receipt.location().namespace().regionPolicy().name(),
                receipt.location().namespace().dataClassification().name(),
                receipt.location().opaqueLocator(), receipt.location().providerVersionToken(),
                receipt.location().region(), receipt.committedDigest().algorithm().name(),
                receipt.committedDigest().canonicalValue(), receipt.committedLength(),
                receipt.providerCorrelationId(), issuedAt);
    }
}
