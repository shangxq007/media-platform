package com.example.platform.render.app;

import com.example.platform.shared.Ids;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.QuotaUsage.QUOTA_USAGE;


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
            dsl.update(QUOTA_USAGE)
                    .set(QUOTA_USAGE.USAGE_VALUE, newValue)
                    .set(QUOTA_USAGE.UPDATED_AT, LocalDateTime.now())
                    .where(QUOTA_USAGE.ID.eq(existing.get().id()))
                    .execute();
            return newValue;
        } else {
            String id = Ids.newId("qtu");
            dsl.insertInto(QUOTA_USAGE)
                    .columns(QUOTA_USAGE.ID, QUOTA_USAGE.TENANT_ID, QUOTA_USAGE.FEATURE_CODE,
                            QUOTA_USAGE.USAGE_VALUE, QUOTA_USAGE.CREATED_AT, QUOTA_USAGE.UPDATED_AT)
                    .values(id, tenantId, featureCode, amount, LocalDateTime.now(), LocalDateTime.now())
                    .execute();
            return amount;
        }
    }

    public Optional<QuotaUsageRecord> findByTenantAndFeature(String tenantId, String featureCode) {
        Record record = dsl.select()
                .from(QUOTA_USAGE)
                .where(QUOTA_USAGE.TENANT_ID.eq(tenantId))
                .and(QUOTA_USAGE.FEATURE_CODE.eq(featureCode))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public int getUsage(String tenantId, String featureCode) {
        return findByTenantAndFeature(tenantId, featureCode)
                .map(QuotaUsageRecord::usageValue)
                .orElse(0);
    }

    public Map<String, Integer> getUsageByTenant(String tenantId) {
        return dsl.select(QUOTA_USAGE.FEATURE_CODE, QUOTA_USAGE.USAGE_VALUE)
                .from(QUOTA_USAGE)
                .where(QUOTA_USAGE.TENANT_ID.eq(tenantId))
                .fetchMap(
                        r -> r.get(QUOTA_USAGE.FEATURE_CODE, String.class),
                        r -> r.get(QUOTA_USAGE.USAGE_VALUE, Integer.class)
                );
    }

    private QuotaUsageRecord mapRecord(Record record) {
        return new QuotaUsageRecord(
                record.get(QUOTA_USAGE.ID, String.class),
                record.get(QUOTA_USAGE.TENANT_ID, String.class),
                record.get(QUOTA_USAGE.FEATURE_CODE, String.class),
                record.get(QUOTA_USAGE.USAGE_VALUE, Integer.class),
                record.get(QUOTA_USAGE.CREATED_AT, OffsetDateTime.class),
                record.get(QUOTA_USAGE.UPDATED_AT, OffsetDateTime.class)
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
