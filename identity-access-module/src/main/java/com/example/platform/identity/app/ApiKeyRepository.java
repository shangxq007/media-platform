package com.example.platform.identity.app;

import com.example.platform.identity.infrastructure.JooqRecords;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.ApiKey.API_KEY;


@Repository
public class ApiKeyRepository {

    private final DSLContext dsl;

    public ApiKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ApiKeyRecord save(ApiKeyRecord record) {
        dsl.insertInto(API_KEY)
                .columns(API_KEY.ID, API_KEY.TENANT_ID, API_KEY.FINGERPRINT,
                        API_KEY.HASHED_KEY, API_KEY.PRINCIPAL, API_KEY.CREATED_AT,
                        API_KEY.LAST_USED_AT, API_KEY.REVOKED_AT)
                .values(record.id(), record.tenantId(), record.fingerprint(),
                        record.hashedKey(), record.principal(), toLocal(record.createdAt()),
                        toLocal(record.lastUsedAt()), toLocal(record.revokedAt()))
                .execute();
        return record;
    }

    public Optional<ApiKeyRecord> findByHashedKey(String hashedKey) {
        Record record = dsl.select()
                .from(API_KEY)
                .where(API_KEY.HASHED_KEY.eq(hashedKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<ApiKeyRecord> findByFingerprint(String fingerprint) {
        Record record = dsl.select()
                .from(API_KEY)
                .where(API_KEY.FINGERPRINT.eq(fingerprint))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<ApiKeyRecord> findAll() {
        return dsl.select()
                .from(API_KEY)
                .orderBy(API_KEY.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void updateLastUsedAt(String hashedKey, OffsetDateTime lastUsedAt) {
        dsl.update(API_KEY)
                .set(API_KEY.LAST_USED_AT, lastUsedAt != null ? lastUsedAt.toLocalDateTime() : null)
                .where(API_KEY.HASHED_KEY.eq(hashedKey))
                .execute();
    }

    public void updateRevokedAt(String hashedKey, OffsetDateTime revokedAt) {
        dsl.update(API_KEY)
                .set(API_KEY.REVOKED_AT, revokedAt != null ? revokedAt.toLocalDateTime() : null)
                .where(API_KEY.HASHED_KEY.eq(hashedKey))
                .execute();
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private ApiKeyRecord mapRecord(Record record) {
        OffsetDateTime lastUsed = JooqRecords.offsetDateTime(record, "last_used_at");
        OffsetDateTime revoked = JooqRecords.offsetDateTime(record, "revoked_at");
        return new ApiKeyRecord(
                JooqRecords.string(record, "id"),
                JooqRecords.string(record, "tenant_id"),
                JooqRecords.string(record, "fingerprint"),
                JooqRecords.string(record, "hashed_key"),
                JooqRecords.string(record, "principal"),
                JooqRecords.offsetDateTime(record, "created_at").toInstant(),
                lastUsed != null ? lastUsed.toInstant() : null,
                revoked != null ? revoked.toInstant() : null
        );
    }
}
