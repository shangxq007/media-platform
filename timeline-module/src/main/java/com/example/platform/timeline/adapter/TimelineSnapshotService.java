package com.example.platform.timeline.adapter;

import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;


@Service
public class TimelineSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(TimelineSnapshotService.class);

    private final DSLContext dsl;

    public TimelineSnapshotService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public String save(String projectId, String tenantId, String payloadJson, String schemaVersion) {
        return saveTx(dsl, projectId, tenantId, payloadJson, schemaVersion);
    }

    /** CHECKPOINT_A (Round 3): transaction-scoped snapshot write joining the
     *  caller's jOOQ transaction (rollback-safe with revision + pins). */
    public String saveTx(org.jooq.DSLContext tx, String projectId, String tenantId,
                         String payloadJson, String schemaVersion) {
        String snapshotId = Ids.newId("snap");
        String effectiveTenant = tenantId != null ? tenantId : TenantContext.get();
        tx.insertInto(TIMELINE_SNAPSHOT)
                .columns(TIMELINE_SNAPSHOT.ID, TIMELINE_SNAPSHOT.PROJECT_ID, TIMELINE_SNAPSHOT.TENANT_ID,
                        TIMELINE_SNAPSHOT.PAYLOAD_JSON, TIMELINE_SNAPSHOT.SCHEMA_VERSION, TIMELINE_SNAPSHOT.CREATED_AT)
                .values(snapshotId, projectId, effectiveTenant, payloadJson,
                        schemaVersion != null ? schemaVersion : "2.0.0", LocalDateTime.now())
                .execute();
        log.info("Saved timeline snapshot id={} project={}", snapshotId, projectId);
        return snapshotId;
    }

    public Optional<String> findPayload(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        Record record = dsl.select(TIMELINE_SNAPSHOT.PAYLOAD_JSON)
                .from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.ID.eq(snapshotId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(record.get(TIMELINE_SNAPSHOT.PAYLOAD_JSON));
    }

    /**
     * ROADMAP20 FINAL (R1): ownership-scoped authoritative Timeline snapshot
     * read (TIMELINE_SNAPSHOT_LOOKUP_IS_PROJECT_AND_TENANT_SCOPED_V1) —
     * resolves ONLY a snapshot bound to (projectId, tenantId). Identity
     * uniqueness != ownership authority. Canonical restore / verification /
     * hydration MUST use this path; never unscoped {@link #findPayload}.
     */
    /** Convenience overload using this service's DSL (non-transactional reads). */
    public Optional<SnapshotInfo> findOwnedById(String projectId, String tenantId,
                                                String snapshotId) {
        return findOwnedById(dsl, projectId, tenantId, snapshotId);
    }

    public Optional<SnapshotInfo> findOwnedById(org.jooq.DSLContext readDsl,
                                                String projectId, String tenantId,
                                                String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        Record record = readDsl.select(
                        TIMELINE_SNAPSHOT.ID,
                        TIMELINE_SNAPSHOT.PROJECT_ID,
                        TIMELINE_SNAPSHOT.TENANT_ID,
                        TIMELINE_SNAPSHOT.PAYLOAD_JSON,
                        TIMELINE_SNAPSHOT.SCHEMA_VERSION)
                .from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.ID.eq(snapshotId))
                .and(TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId))
                .and(TIMELINE_SNAPSHOT.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshotInfo(record));
    }

    public Optional<SnapshotInfo> findLatestByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        Record record = dsl.select(
                        TIMELINE_SNAPSHOT.ID,
                        TIMELINE_SNAPSHOT.PROJECT_ID,
                        TIMELINE_SNAPSHOT.TENANT_ID,
                        TIMELINE_SNAPSHOT.PAYLOAD_JSON,
                        TIMELINE_SNAPSHOT.SCHEMA_VERSION)
                .from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId))
                .orderBy(TIMELINE_SNAPSHOT.CREATED_AT.desc())
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshotInfo(record));
    }

    public List<String> listDistinctProjectIds() {
        return dsl.selectDistinct(TIMELINE_SNAPSHOT.PROJECT_ID)
                .from(TIMELINE_SNAPSHOT)
                .fetch(TIMELINE_SNAPSHOT.PROJECT_ID);
    }

    public Optional<SnapshotInfo> findById(String snapshotId) {
        Record record = dsl.select(
                        TIMELINE_SNAPSHOT.ID,
                        TIMELINE_SNAPSHOT.PROJECT_ID,
                        TIMELINE_SNAPSHOT.TENANT_ID,
                        TIMELINE_SNAPSHOT.PAYLOAD_JSON,
                        TIMELINE_SNAPSHOT.SCHEMA_VERSION)
                .from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.ID.eq(snapshotId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshotInfo(record));
    }

    private static SnapshotInfo mapSnapshotInfo(Record record) {
        return new SnapshotInfo(
                record.get(TIMELINE_SNAPSHOT.ID),
                record.get(TIMELINE_SNAPSHOT.PROJECT_ID),
                record.get(TIMELINE_SNAPSHOT.TENANT_ID),
                record.get(TIMELINE_SNAPSHOT.PAYLOAD_JSON),
                record.get(TIMELINE_SNAPSHOT.SCHEMA_VERSION)
        );
    }

    public record SnapshotInfo(
            String id,
            String projectId,
            String tenantId,
            String payloadJson,
            String schemaVersion) {}
}
