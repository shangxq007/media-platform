package com.example.platform.commerce.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

/**
 * Persistence repository for purchase orders.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository
@ConditionalOnBean(DSLContext.class)
public class PurchaseOrderRepository {

    private final DSLContext dsl;

    public PurchaseOrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String orderId, String checkoutSessionId, String canonicalProductCode,
                     String orderStatus, Long amountMinor, String currencyCode) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("PURCHASE_ORDER"))
                .columns(field("ID"), field("CHECKOUT_SESSION_ID"), field("CANONICAL_PRODUCT_CODE"),
                        field("ORDER_STATUS"), field("TOTAL_AMOUNT_MINOR"), field("CURRENCY_CODE"),
                        field("CREATED_AT"))
                .values(orderId, checkoutSessionId, canonicalProductCode,
                        orderStatus, amountMinor, currencyCode, now)
                .execute();
    }

    public Optional<PurchaseOrderRecord> findById(String id) {
        Record record = dsl.select()
                .from(table("PURCHASE_ORDER"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(r -> new PurchaseOrderRecord(
                r.get(field("ID"), String.class),
                r.get(field("CHECKOUT_SESSION_ID"), String.class),
                r.get(field("CANONICAL_PRODUCT_CODE"), String.class),
                r.get(field("ORDER_STATUS"), String.class),
                r.get(field("TOTAL_AMOUNT_MINOR"), Long.class),
                r.get(field("CURRENCY_CODE"), String.class)
        ));
    }

    public Optional<PurchaseOrderRecord> findByCheckoutSessionId(String checkoutSessionId) {
        Record record = dsl.select()
                .from(table("PURCHASE_ORDER"))
                .where(field("CHECKOUT_SESSION_ID").eq(checkoutSessionId))
                .fetchOne();
        return Optional.ofNullable(record).map(r -> new PurchaseOrderRecord(
                r.get(field("ID"), String.class),
                r.get(field("CHECKOUT_SESSION_ID"), String.class),
                r.get(field("CANONICAL_PRODUCT_CODE"), String.class),
                r.get(field("ORDER_STATUS"), String.class),
                r.get(field("TOTAL_AMOUNT_MINOR"), Long.class),
                r.get(field("CURRENCY_CODE"), String.class)
        ));
    }

    /**
     * Flat record for purchase order data from the database.
     */
    public record PurchaseOrderRecord(
            String id,
            String checkoutSessionId,
            String canonicalProductCode,
            String orderStatus,
            Long totalAmountMinor,
            String currencyCode
    ) {}
}
