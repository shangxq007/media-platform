package com.example.platform.render.app;

import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Service
public class TimelineSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(TimelineSnapshotService.class);

    private final DSLContext dsl;

    public TimelineSnapshotService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public String save(String projectId, String tenantId, String payloadJson, String schemaVersion) {
        String snapshotId = Ids.newId("snap");
        String effectiveTenant = tenantId != null ? tenantId : TenantContext.get();
        dsl.insertInto(table("timeline_snapshot"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("payload_json"), field("schema_version"), field("created_at"))
                .values(snapshotId, projectId, effectiveTenant, payloadJson,
                        schemaVersion != null ? schemaVersion : "2.0.0", OffsetDateTime.now())
                .execute();
        log.info("Saved timeline snapshot id={} project={}", snapshotId, projectId);
        return snapshotId;
    }

    public Optional<String> findPayload(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        Record record = dsl.select(field("payload_json", String.class))
                .from(table("timeline_snapshot"))
                .where(field("id").eq(snapshotId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(record.get(field("payload_json", String.class)));
    }

    public Optional<SnapshotInfo> findLatestByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        Record record = dsl.select(
                        field("id", String.class),
                        field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("payload_json", String.class),
                        field("schema_version", String.class))
                .from(table("timeline_snapshot"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc())
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshotInfo(record));
    }

    public List<String> listDistinctProjectIds() {
        return dsl.selectDistinct(field("project_id", String.class))
                .from(table("timeline_snapshot"))
                .fetch(field("project_id", String.class));
    }

    public Optional<SnapshotInfo> findById(String snapshotId) {
        Record record = dsl.select(
                        field("id", String.class),
                        field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("payload_json", String.class),
                        field("schema_version", String.class))
                .from(table("timeline_snapshot"))
                .where(field("id").eq(snapshotId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshotInfo(record));
    }

    private static SnapshotInfo mapSnapshotInfo(Record record) {
        return new SnapshotInfo(
                record.get(field("id", String.class)),
                record.get(field("project_id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("payload_json", String.class)),
                record.get(field("schema_version", String.class))
        );
    }

    public record SnapshotInfo(
            String id,
            String projectId,
            String tenantId,
            String payloadJson,
            String schemaVersion) {}
}
