package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class QuotaUsageRepository {

    private final DSLContext dsl;

    public QuotaUsageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int incrementUsage(String tenantId, String featureCode, int amount) {
        Optional<QuotaUsageRecord> existing = findByTenantAndFeature(tenantId, featureCode);
        if (existing.isPresent()) {
            int newValue = existing.get().usageValue() + amount;
            dsl.update(table("quota_usage"))
                    .set(field("usage_value"), newValue)
                    .set(field("updated_at"), OffsetDateTime.now())
                    .where(field("id").eq(existing.get().id()))
                    .execute();
            return newValue;
        } else {
            String id = Ids.newId("qtu");
            dsl.insertInto(table("quota_usage"))
                    .columns(field("id"), field("tenant_id"), field("feature_code"),
                            field("usage_value"), field("created_at"), field("updated_at"))
                    .values(id, tenantId, featureCode, amount, OffsetDateTime.now(), OffsetDateTime.now())
                    .execute();
            return amount;
        }
    }

    public Optional<QuotaUsageRecord> findByTenantAndFeature(String tenantId, String featureCode) {
        Record record = dsl.select()
                .from(table("quota_usage"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("feature_code").eq(featureCode))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public int getUsage(String tenantId, String featureCode) {
        return findByTenantAndFeature(tenantId, featureCode)
                .map(QuotaUsageRecord::usageValue)
                .orElse(0);
    }

    public Map<String, Integer> getUsageByTenant(String tenantId) {
        return dsl.select(field("feature_code"), field("usage_value"))
                .from(table("quota_usage"))
                .where(field("tenant_id").eq(tenantId))
                .fetchMap(
                        r -> r.get(field("feature_code"), String.class),
                        r -> r.get(field("usage_value"), Integer.class)
                );
    }

    private QuotaUsageRecord mapRecord(Record record) {
        return new QuotaUsageRecord(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("feature_code"), String.class),
                record.get(field("usage_value"), Integer.class),
                record.get(field("created_at"), OffsetDateTime.class),
                record.get(field("updated_at"), OffsetDateTime.class)
        );
    }

    public record QuotaUsageRecord(
            String id,
            String tenantId,
            String featureCode,
            int usageValue,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}
}
