package com.example.platform.storage.infrastructure.migration;

import com.example.platform.storage.app.migration.StorageMigrationObservationRepository;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationResult;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.EvidenceReference;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.Outcome;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL observation persistence; it has no write path to migration-input rows. */
@Repository
public class JdbcStorageMigrationObservationRepository
        implements StorageMigrationObservationRepository {

    private final JdbcTemplate jdbc;

    public JdbcStorageMigrationObservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordDatabaseBinding(StorageDatabaseBinding binding) {
        jdbc.update("""
                insert into storage_database_binding (
                    binding_id, database_kind, database_identity, deployment_identity,
                    environment_identity, schema_identity, schema_version,
                    query_evidence_version, binding_evidence_ref, is_canonical, observed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (binding_id) do nothing
                """,
                binding.bindingId(), binding.databaseKind().name(), binding.databaseIdentity(),
                binding.deploymentIdentity(), binding.environmentIdentity().name(),
                binding.schemaIdentity(), binding.schemaVersion(), binding.queryEvidenceVersion(),
                binding.bindingEvidenceRef(), binding.canonical(),
                OffsetDateTime.ofInstant(binding.observedAt(), ZoneOffset.UTC));

        StorageDatabaseBinding persisted = jdbc.queryForObject("""
                select binding_id, database_kind, database_identity, deployment_identity,
                       environment_identity, schema_identity, schema_version,
                       query_evidence_version, binding_evidence_ref, is_canonical, observed_at
                  from storage_database_binding where binding_id = ?
                """, (rs, rowNumber) -> new StorageDatabaseBinding(
                rs.getString("binding_id"),
                DatabaseKind.valueOf(rs.getString("database_kind")),
                rs.getString("database_identity"),
                rs.getString("deployment_identity"),
                DeploymentEnvironment.valueOf(rs.getString("environment_identity")),
                rs.getString("schema_identity"),
                rs.getString("schema_version"),
                rs.getString("query_evidence_version"),
                rs.getString("binding_evidence_ref"),
                rs.getBoolean("is_canonical"),
                rs.getObject("observed_at", OffsetDateTime.class).toInstant()),
                binding.bindingId());
        if (!binding.equals(persisted)) {
            throw new IllegalStateException("database binding ID was already used with different evidence");
        }
    }

    @Override
    public ClassificationResult saveOrLoadClassification(ClassificationResult classification) {
        int inserted = jdbc.update("""
                insert into storage_identity_classification (
                    classification_id, database_binding_id, source_table,
                    source_primary_identity, original_persisted_value, classifier_version,
                    evidence_version, evidence_fingerprint, outcome, observed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (classification_id) do nothing
                """,
                classification.classificationId(), classification.databaseBindingId(),
                classification.sourceTable(), classification.sourcePrimaryIdentity(),
                classification.originalPersistedValue(), classification.classifierVersion(),
                classification.evidenceVersion(), classification.evidenceFingerprint(),
                classification.outcome().name(),
                OffsetDateTime.ofInstant(classification.observedAt(), ZoneOffset.UTC));
        if (inserted == 1) {
            int ordinal = 0;
            for (EvidenceReference evidence : classification.selectedEvidenceReferences()) {
                jdbc.update("""
                        insert into storage_identity_classification_evidence (
                            classification_id, evidence_ordinal, evidence_type, evidence_ref
                        ) values (?, ?, ?, ?)
                        """, classification.classificationId(), ordinal++,
                        evidence.type(), evidence.reference());
            }
            return classification;
        }

        ClassificationResult persisted = loadClassification(classification.classificationId());
        if (!sameSemanticObservation(classification, persisted)) {
            throw new IllegalStateException(
                    "classification ID was already used with different evidence or outcome");
        }
        return persisted;
    }

    private ClassificationResult loadClassification(String classificationId) {
        List<EvidenceReference> references = jdbc.query("""
                select evidence_type, evidence_ref
                  from storage_identity_classification_evidence
                 where classification_id = ?
                 order by evidence_ordinal
                """, (rs, rowNumber) -> new EvidenceReference(
                rs.getString("evidence_type"), rs.getString("evidence_ref")), classificationId);
        return jdbc.queryForObject("""
                select classification_id, database_binding_id, source_table,
                       source_primary_identity, original_persisted_value, classifier_version,
                       evidence_version, evidence_fingerprint, outcome, observed_at
                  from storage_identity_classification where classification_id = ?
                """, (rs, rowNumber) -> new ClassificationResult(
                rs.getString("classification_id"),
                rs.getString("database_binding_id"),
                rs.getString("source_table"),
                rs.getString("source_primary_identity"),
                rs.getString("original_persisted_value"),
                rs.getString("classifier_version"),
                rs.getString("evidence_version"),
                rs.getString("evidence_fingerprint"),
                references,
                Outcome.valueOf(rs.getString("outcome")),
                rs.getObject("observed_at", OffsetDateTime.class).toInstant()),
                classificationId);
    }

    private static boolean sameSemanticObservation(
            ClassificationResult requested,
            ClassificationResult persisted) {
        return requested.classificationId().equals(persisted.classificationId())
                && requested.databaseBindingId().equals(persisted.databaseBindingId())
                && requested.sourceTable().equals(persisted.sourceTable())
                && requested.sourcePrimaryIdentity().equals(persisted.sourcePrimaryIdentity())
                && requested.originalPersistedValue().equals(persisted.originalPersistedValue())
                && requested.classifierVersion().equals(persisted.classifierVersion())
                && requested.evidenceVersion().equals(persisted.evidenceVersion())
                && requested.evidenceFingerprint().equals(persisted.evidenceFingerprint())
                && requested.selectedEvidenceReferences().equals(persisted.selectedEvidenceReferences())
                && requested.outcome() == persisted.outcome();
    }
}
