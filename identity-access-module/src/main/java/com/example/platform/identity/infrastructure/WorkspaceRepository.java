package com.example.platform.identity.infrastructure;

import com.example.platform.identity.domain.Workspace;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Workspace.WORKSPACE;


@Repository

public class WorkspaceRepository {

    private final DSLContext dsl;

    public WorkspaceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Workspace save(Workspace workspace) {
        dsl.insertInto(WORKSPACE)
                .columns(WORKSPACE.ID, WORKSPACE.TENANT_ID, WORKSPACE.NAME,
                        WORKSPACE.DESCRIPTION, WORKSPACE.PLAN_TIER, WORKSPACE.STATUS,
                        WORKSPACE.CREATED_AT, WORKSPACE.UPDATED_AT)
                .values(workspace.id(), workspace.tenantId(), workspace.name(),
                        workspace.description(), workspace.planTier(),
                        workspace.status().name(),
                        LocalDateTime.ofInstant(workspace.createdAt(), ZoneOffset.UTC),
                        LocalDateTime.ofInstant(workspace.updatedAt(), ZoneOffset.UTC))
                .execute();
        return workspace;
    }

    public Optional<Workspace> findById(String id) {
        Record record = dsl.select()
                .from(WORKSPACE)
                .where(WORKSPACE.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Workspace> findByTenantId(String tenantId) {
        return dsl.select()
                .from(WORKSPACE)
                .where(WORKSPACE.TENANT_ID.eq(tenantId))
                .orderBy(WORKSPACE.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<Workspace> findAll() {
        return dsl.select()
                .from(WORKSPACE)
                .orderBy(WORKSPACE.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void updateStatus(String id, Workspace.WorkspaceStatus status, OffsetDateTime updatedAt) {
        dsl.update(WORKSPACE)
                .set(WORKSPACE.STATUS, status.name())
                .set(WORKSPACE.UPDATED_AT, updatedAt.toLocalDateTime())
                .where(WORKSPACE.ID.eq(id))
                .execute();
    }

    private Workspace mapRecord(Record record) {
        return new Workspace(
                record.get(WORKSPACE.ID, String.class),
                record.get(WORKSPACE.TENANT_ID, String.class),
                record.get(WORKSPACE.NAME, String.class),
                record.get(WORKSPACE.DESCRIPTION, String.class),
                record.get(WORKSPACE.PLAN_TIER, String.class),
                Workspace.WorkspaceStatus.valueOf(record.get(WORKSPACE.STATUS, String.class)),
                record.get(WORKSPACE.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                record.get(WORKSPACE.UPDATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
