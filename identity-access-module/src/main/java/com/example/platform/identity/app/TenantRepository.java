package com.example.platform.identity.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.infrastructure.JooqRecords;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private final DSLContext dsl;

    public TenantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Tenant save(Tenant tenant) {
        dsl.insertInto(table("tenant"))
                .columns(field("id"), field("name"), field("status"), field("created_at"))
                .values(tenant.id(), tenant.name(), tenant.status().name(), tenant.createdAt())
                .execute();
        return tenant;
    }

    public Optional<Tenant> findById(String id) {
        Record record = dsl.select()
                .from(table("tenant"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Tenant> findAll() {
        return dsl.select()
                .from(table("tenant"))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    private Tenant mapRecord(Record record) {
        return new Tenant(
                JooqRecords.string(record, "id"),
                JooqRecords.string(record, "name"),
                Tenant.TenantStatus.valueOf(JooqRecords.string(record, "status")),
                JooqRecords.offsetDateTime(record, "created_at").toInstant()
        );
    }
}
