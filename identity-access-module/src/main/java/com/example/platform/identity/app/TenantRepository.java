package com.example.platform.identity.app;

import com.example.platform.identity.domain.Tenant;
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
import static com.example.platform.typedschema.jooq.generated.tables.Tenant.TENANT;


@Repository
public class TenantRepository {

    private final DSLContext dsl;

    public TenantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Tenant save(Tenant tenant) {
        dsl.insertInto(TENANT)
                .columns(TENANT.ID, TENANT.NAME, TENANT.STATUS, TENANT.CREATED_AT)
                .values(tenant.id(), tenant.name(), tenant.status().name(), LocalDateTime.ofInstant(tenant.createdAt(), ZoneOffset.UTC))
                .execute();
        return tenant;
    }

    public Optional<Tenant> findById(String id) {
        Record record = dsl.select()
                .from(TENANT)
                .where(TENANT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Tenant> findAll() {
        return findAll(100);
    }

    public List<Tenant> findAll(int limit) {
        return dsl.select()
                .from(TENANT)
                .orderBy(TENANT.CREATED_AT.desc())
                .limit(limit)
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
