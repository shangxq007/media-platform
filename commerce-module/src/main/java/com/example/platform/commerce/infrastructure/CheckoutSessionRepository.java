package com.example.platform.commerce.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.commerce.domain.CheckoutSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

/**
 * Persistence repository for {@link CheckoutSession} entities.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * The {@link com.example.platform.commerce.app.CheckoutOrchestrator} falls back
 * to in-memory storage when this repository is not available.</p>
 *
 * <p><strong>Note:</strong> H2 in PostgreSQL mode stores column names in uppercase.
 * We use uppercase field references for all operations.</p>
 */
@Repository
@ConditionalOnBean(DSLContext.class)
public class CheckoutSessionRepository {

    private final DSLContext dsl;

    public CheckoutSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CheckoutSession save(CheckoutSession session) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("CHECKOUT_SESSION"))
                .columns(field("ID"), field("CHECKOUT_SESSION_CODE"), field("TENANT_ID"), field("PRODUCT_ID"),
                        field("PROVIDER_CODE"), field("SESSION_STATUS"), field("SUCCESS_URL"),
                        field("CANCEL_URL"), field("CREATED_AT"))
                .values(session.checkoutSessionId(), session.checkoutSessionId(), session.tenantId(),
                        session.canonicalProductCode(), session.providerHint(),
                        "PENDING", session.redirectUrl(), "", now)
                .execute();
        return session;
    }

    public Optional<CheckoutSession> findById(String id) {
        Record record = dsl.select()
                .from(table("CHECKOUT_SESSION"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public void updateStatus(String id, String status) {
        dsl.update(table("CHECKOUT_SESSION"))
                .set(field("SESSION_STATUS"), status)
                .where(field("ID").eq(id))
                .execute();
    }

    private CheckoutSession mapRecord(Record record) {
        return new CheckoutSession(
                record.get(field("ID"), String.class),
                record.get(field("TENANT_ID"), String.class),
                record.get(field("PRODUCT_ID"), String.class),
                record.get(field("SUCCESS_URL"), String.class),
                record.get(field("PROVIDER_CODE"), String.class)
        );
    }
}
