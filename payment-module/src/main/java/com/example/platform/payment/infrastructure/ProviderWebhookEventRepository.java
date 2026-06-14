package com.example.platform.payment.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

/**
 * Persistence repository for provider webhook events.
 *
 * <p>Supports idempotency checks via {@link #existsByKey(String)} — if a webhook
 * event with the same key has already been processed, the event is skipped.</p>
 *
 * <p>Only created when a {@link DSLContext} bean is available.</p>
 */
@Repository

public class ProviderWebhookEventRepository {

    private final DSLContext dsl;

    public ProviderWebhookEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String providerCode, String webhookEventKey, String webhookEventType,
                     int webhookEventVersion, boolean signatureValid, String payload) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("PROVIDER_WEBHOOK_EVENT"))
                .columns(field("ID"), field("PROVIDER_CODE"), field("WEBHOOK_EVENT_KEY"),
                        field("WEBHOOK_EVENT_TYPE"), field("WEBHOOK_EVENT_VERSION"),
                        field("SIGNATURE_VALID"), field("PAYLOAD"), field("CREATED_AT"))
                .values(webhookEventKey, providerCode, webhookEventKey, webhookEventType,
                        webhookEventVersion, signatureValid, payload, now)
                .execute();
    }

    public boolean existsByKey(String webhookEventKey) {
        Integer count = dsl.selectCount()
                .from(table("PROVIDER_WEBHOOK_EVENT"))
                .where(field("WEBHOOK_EVENT_KEY").eq(webhookEventKey))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    public Optional<WebhookEventRecord> findByKey(String webhookEventKey) {
        Record record = dsl.select()
                .from(table("PROVIDER_WEBHOOK_EVENT"))
                .where(field("WEBHOOK_EVENT_KEY").eq(webhookEventKey))
                .fetchOne();
        return Optional.ofNullable(record).map(r -> new WebhookEventRecord(
                r.get(field("ID"), String.class),
                r.get(field("PROVIDER_CODE"), String.class),
                r.get(field("WEBHOOK_EVENT_KEY"), String.class),
                r.get(field("WEBHOOK_EVENT_TYPE"), String.class),
                r.get(field("WEBHOOK_EVENT_VERSION"), Integer.class),
                r.get(field("SIGNATURE_VALID"), Boolean.class),
                r.get(field("PAYLOAD"), String.class)
        ));
    }

    /**
     * Flat record for webhook event data from the database.
     */
    public record WebhookEventRecord(
            String id,
            String providerCode,
            String webhookEventKey,
            String webhookEventType,
            int webhookEventVersion,
            boolean signatureValid,
            String payload
    ) {}
}
