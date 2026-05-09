package com.example.platform.identity.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.Project;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final DSLContext dsl;

    public ProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Project save(Project project) {
        dsl.insertInto(table("project"))
                .columns(field("id"), field("tenant_id"), field("name"),
                        field("description"), field("status"), field("created_at"))
                .values(project.id(), project.tenantId(), project.name(),
                        project.description(), project.status().name(), project.createdAt())
                .execute();
        return project;
    }

    public Optional<Project> findById(String id) {
        Record record = dsl.select()
                .from(table("project"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Project> findByTenantId(String tenantId) {
        return dsl.select()
                .from(table("project"))
                .where(field("tenant_id").eq(tenantId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    private Project mapRecord(Record record) {
        return new Project(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("name"), String.class),
                record.get(field("description"), String.class),
                Project.ProjectStatus.valueOf(record.get(field("status"), String.class)),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }
}
