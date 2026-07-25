package com.example.platform.identity.app;

import com.example.platform.identity.domain.Project;
import com.example.platform.identity.infrastructure.JooqRecords;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Project.PROJECT;


@Repository
public class ProjectRepository {

    private final DSLContext dsl;

    public ProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Project save(Project project) {
        dsl.insertInto(PROJECT)
                .columns(PROJECT.ID, PROJECT.TENANT_ID, PROJECT.NAME,
                        PROJECT.DESCRIPTION, PROJECT.STATUS, PROJECT.CREATED_AT)
                .values(project.id(), project.tenantId(), project.name(),
                        project.description(), project.status().name(), LocalDateTime.ofInstant(project.createdAt(), ZoneOffset.UTC))
                .execute();
        return project;
    }

    public Optional<Project> findById(String id) {
        Record record = dsl.select()
                .from(PROJECT)
                .where(PROJECT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Project> findByTenantId(String tenantId) {
        return dsl.select()
                .from(PROJECT)
                .where(PROJECT.TENANT_ID.eq(tenantId))
                .orderBy(PROJECT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void deleteById(String id) {
        dsl.deleteFrom(PROJECT)
                .where(PROJECT.ID.eq(id))
                .execute();
    }

    private Project mapRecord(Record record) {
        return new Project(
                JooqRecords.string(record, "id"),
                JooqRecords.string(record, "tenant_id"),
                JooqRecords.string(record, "name"),
                JooqRecords.string(record, "description"),
                Project.ProjectStatus.valueOf(JooqRecords.string(record, "status")),
                JooqRecords.offsetDateTime(record, "created_at").toInstant()
        );
    }
}
