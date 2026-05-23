package com.example.platform.app.ai;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class TenantLitellmKeyRepository {

    private final DSLContext dsl;

    public TenantLitellmKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<TenantLitellmKeyRecord> findByTenantId(String tenantId) {
        Record row = dsl.select()
                .from(table("tenant_litellm_virtual_key"))
                .where(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(map(row));
    }

    public void upsert(
            String tenantId, String virtualKey, String vaultRef, String keyAlias, boolean enabled) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = dsl.update(table("tenant_litellm_virtual_key"))
                .set(field("virtual_key"), virtualKey)
                .set(field("vault_ref"), vaultRef)
                .set(field("key_alias"), keyAlias)
                .set(field("enabled"), enabled)
                .set(field("updated_at"), now)
                .where(field("tenant_id").eq(tenantId))
                .execute();
        if (updated == 0) {
            dsl.insertInto(table("tenant_litellm_virtual_key"))
                    .columns(
                            field("tenant_id"),
                            field("virtual_key"),
                            field("vault_ref"),
                            field("key_alias"),
                            field("enabled"),
                            field("created_at"),
                            field("updated_at"))
                    .values(tenantId, virtualKey, vaultRef, keyAlias, enabled, now, now)
                    .execute();
        }
    }

    public void delete(String tenantId) {
        dsl.deleteFrom(table("tenant_litellm_virtual_key"))
                .where(field("tenant_id").eq(tenantId))
                .execute();
    }

    /** Rows that may still hold plaintext virtual keys (vault_ref empty). */
    public List<TenantLitellmKeyRecord> findAllInlineKeys() {
        List<Record> rows = dsl.select()
                .from(table("tenant_litellm_virtual_key"))
                .fetch();
        List<TenantLitellmKeyRecord> result = new ArrayList<>();
        for (Record row : rows) {
            result.add(map(row));
        }
        return result;
    }

    private static TenantLitellmKeyRecord map(Record row) {
        return new TenantLitellmKeyRecord(
                row.get(field("tenant_id", String.class)),
                row.get(field("virtual_key", String.class)),
                row.get(field("vault_ref", String.class)),
                row.get(field("key_alias", String.class)),
                Boolean.TRUE.equals(row.get(field("enabled", Boolean.class))),
                toOffsetDateTime(row.get(field("created_at"))),
                toOffsetDateTime(row.get(field("updated_at"))));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
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
