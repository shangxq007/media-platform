package com.example.platform.app.ai;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.TenantLitellmVirtualKey.TENANT_LITELLM_VIRTUAL_KEY;


@Repository
public class TenantLitellmKeyRepository {

    private final DSLContext dsl;

    public TenantLitellmKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<TenantLitellmKeyRecord> findByTenantId(String tenantId) {
        Record row = dsl.select()
                .from(TENANT_LITELLM_VIRTUAL_KEY)
                .where(TENANT_LITELLM_VIRTUAL_KEY.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(map(row));
    }

    public void upsert(
            String tenantId, String virtualKey, String vaultRef, String keyAlias, boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        int updated = dsl.update(TENANT_LITELLM_VIRTUAL_KEY)
                .set(TENANT_LITELLM_VIRTUAL_KEY.VIRTUAL_KEY, virtualKey)
                .set(TENANT_LITELLM_VIRTUAL_KEY.VAULT_REF, vaultRef)
                .set(TENANT_LITELLM_VIRTUAL_KEY.KEY_ALIAS, keyAlias)
                .set(TENANT_LITELLM_VIRTUAL_KEY.ENABLED, enabled)
                .set(TENANT_LITELLM_VIRTUAL_KEY.UPDATED_AT, now)
                .where(TENANT_LITELLM_VIRTUAL_KEY.TENANT_ID.eq(tenantId))
                .execute();
        if (updated == 0) {
            dsl.insertInto(TENANT_LITELLM_VIRTUAL_KEY)
                    .columns(
                            TENANT_LITELLM_VIRTUAL_KEY.TENANT_ID,
                            TENANT_LITELLM_VIRTUAL_KEY.VIRTUAL_KEY,
                            TENANT_LITELLM_VIRTUAL_KEY.VAULT_REF,
                            TENANT_LITELLM_VIRTUAL_KEY.KEY_ALIAS,
                            TENANT_LITELLM_VIRTUAL_KEY.ENABLED,
                            TENANT_LITELLM_VIRTUAL_KEY.CREATED_AT,
                            TENANT_LITELLM_VIRTUAL_KEY.UPDATED_AT)
                    .values(tenantId, virtualKey, vaultRef, keyAlias, enabled, now, now)
                    .execute();
        }
    }

    public void delete(String tenantId) {
        dsl.deleteFrom(TENANT_LITELLM_VIRTUAL_KEY)
                .where(TENANT_LITELLM_VIRTUAL_KEY.TENANT_ID.eq(tenantId))
                .execute();
    }

    /** Rows that may still hold plaintext virtual keys (vault_ref empty). */
    public List<TenantLitellmKeyRecord> findAllInlineKeys() {
        List<Record> rows = dsl.select()
                .from(TENANT_LITELLM_VIRTUAL_KEY)
                .fetch();
        List<TenantLitellmKeyRecord> result = new ArrayList<>();
        for (Record row : rows) {
            result.add(map(row));
        }
        return result;
    }

    private static TenantLitellmKeyRecord map(Record row) {
        return new TenantLitellmKeyRecord(
                row.get(TENANT_LITELLM_VIRTUAL_KEY.TENANT_ID),
                row.get(TENANT_LITELLM_VIRTUAL_KEY.VIRTUAL_KEY),
                row.get(TENANT_LITELLM_VIRTUAL_KEY.VAULT_REF),
                row.get(TENANT_LITELLM_VIRTUAL_KEY.KEY_ALIAS),
                Boolean.TRUE.equals(row.get(TENANT_LITELLM_VIRTUAL_KEY.ENABLED)),
                toOffsetDateTime(row.get(TENANT_LITELLM_VIRTUAL_KEY.CREATED_AT)),
                toOffsetDateTime(row.get(TENANT_LITELLM_VIRTUAL_KEY.UPDATED_AT)));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    public record TenantLitellmKeyRecord(
            String tenantId,
            String virtualKey,
            String vaultRef,
            String keyAlias,
            boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}
}
