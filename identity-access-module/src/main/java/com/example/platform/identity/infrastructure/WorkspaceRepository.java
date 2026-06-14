package com.example.platform.identity.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.Workspace;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

@Repository

public class WorkspaceRepository {

    private final DSLContext dsl;

    public WorkspaceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Workspace save(Workspace workspace) {
        dsl.insertInto(table("workspace"))
                .columns(field("id"), field("tenant_id"), field("name"),
                        field("description"), field("plan_tier"), field("status"),
                        field("created_at"), field("updated_at"))
                .values(workspace.id(), workspace.tenantId(), workspace.name(),
                        workspace.description(), workspace.planTier(),
                        workspace.status().name(), workspace.createdAt(), workspace.updatedAt())
                .execute();
        return workspace;
    }

    public Optional<Workspace> findById(String id) {
        Record record = dsl.select()
                .from(table("workspace"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Workspace> findByTenantId(String tenantId) {
        return dsl.select()
                .from(table("workspace"))
                .where(field("tenant_id").eq(tenantId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public List<Workspace> findAll() {
        return dsl.select()
                .from(table("workspace"))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public void updateStatus(String id, Workspace.WorkspaceStatus status, OffsetDateTime updatedAt) {
        dsl.update(table("workspace"))
                .set(field("status"), status.name())
                .set(field("updated_at"), updatedAt)
                .where(field("id").eq(id))
                .execute();
    }

    private Workspace mapRecord(Record record) {
        return new Workspace(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("name"), String.class),
                record.get(field("description"), String.class),
                record.get(field("plan_tier"), String.class),
                Workspace.WorkspaceStatus.valueOf(record.get(field("status"), String.class)),
                record.get(field("created_at"), OffsetDateTime.class).toInstant(),
                record.get(field("updated_at"), OffsetDateTime.class).toInstant()
        );
    }
}
