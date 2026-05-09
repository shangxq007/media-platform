package com.example.platform.identity.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ApiKeyRepository {

    private final DSLContext dsl;

    public ApiKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ApiKeyRecord save(ApiKeyRecord record) {
        dsl.insertInto(table("api_key"))
                .columns(field("id"), field("tenant_id"), field("fingerprint"),
                        field("hashed_key"), field("principal"), field("created_at"),
                        field("last_used_at"), field("revoked_at"))
                .values(record.id(), record.tenantId(), record.fingerprint(),
                        record.hashedKey(), record.principal(), record.createdAt(),
                        record.lastUsedAt(), record.revokedAt())
                .execute();
        return record;
    }

    public Optional<ApiKeyRecord> findByHashedKey(String hashedKey) {
        Record record = dsl.select()
                .from(table("api_key"))
                .where(field("hashed_key").eq(hashedKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<ApiKeyRecord> findByFingerprint(String fingerprint) {
        Record record = dsl.select()
                .from(table("api_key"))
                .where(field("fingerprint").eq(fingerprint))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<ApiKeyRecord> findAll() {
        return dsl.select()
                .from(table("api_key"))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public void updateLastUsedAt(String hashedKey, OffsetDateTime lastUsedAt) {
        dsl.update(table("api_key"))
                .set(field("last_used_at"), lastUsedAt)
                .where(field("hashed_key").eq(hashedKey))
                .execute();
    }

    public void updateRevokedAt(String hashedKey, OffsetDateTime revokedAt) {
        dsl.update(table("api_key"))
                .set(field("revoked_at"), revokedAt)
                .where(field("hashed_key").eq(hashedKey))
                .execute();
    }

    private ApiKeyRecord mapRecord(Record record) {
        return new ApiKeyRecord(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("fingerprint"), String.class),
                record.get(field("hashed_key"), String.class),
                record.get(field("principal"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant(),
                record.get(field("last_used_at"), OffsetDateTime.class) != null
                        ? record.get(field("last_used_at"), OffsetDateTime.class).toInstant() : null,
                record.get(field("revoked_at"), OffsetDateTime.class) != null
                        ? record.get(field("revoked_at"), OffsetDateTime.class).toInstant() : null
        );
    }
}
