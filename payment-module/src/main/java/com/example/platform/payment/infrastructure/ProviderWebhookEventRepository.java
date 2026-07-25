package com.example.platform.payment.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.ProviderWebhookEvent.PROVIDER_WEBHOOK_EVENT;


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
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(PROVIDER_WEBHOOK_EVENT)
                .columns(PROVIDER_WEBHOOK_EVENT.ID, PROVIDER_WEBHOOK_EVENT.PROVIDER_CODE, PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_KEY,
                        PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_TYPE, PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_VERSION,
                        PROVIDER_WEBHOOK_EVENT.SIGNATURE_VALID, PROVIDER_WEBHOOK_EVENT.PAYLOAD, PROVIDER_WEBHOOK_EVENT.CREATED_AT)
                .values(webhookEventKey, providerCode, webhookEventKey, webhookEventType,
                        webhookEventVersion, signatureValid, payload, now)
                .execute();
    }

    public boolean existsByKey(String webhookEventKey) {
        Integer count = dsl.selectCount()
                .from(PROVIDER_WEBHOOK_EVENT)
                .where(PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_KEY.eq(webhookEventKey))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    public Optional<WebhookEventRecord> findByKey(String webhookEventKey) {
        Record record = dsl.select()
                .from(PROVIDER_WEBHOOK_EVENT)
                .where(PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_KEY.eq(webhookEventKey))
                .fetchOne();
        return Optional.ofNullable(record).map(r -> new WebhookEventRecord(
                r.get(PROVIDER_WEBHOOK_EVENT.ID, String.class),
                r.get(PROVIDER_WEBHOOK_EVENT.PROVIDER_CODE, String.class),
                r.get(PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_KEY, String.class),
                r.get(PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_TYPE, String.class),
                r.get(PROVIDER_WEBHOOK_EVENT.WEBHOOK_EVENT_VERSION, Integer.class),
                r.get(PROVIDER_WEBHOOK_EVENT.SIGNATURE_VALID, Boolean.class),
                r.get(PROVIDER_WEBHOOK_EVENT.PAYLOAD, String.class)
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
