package com.example.platform.commerce.infrastructure;

import com.example.platform.shared.web.TenantGuard;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.PurchaseOrder.PURCHASE_ORDER;
import org.jooq.impl.DSL;


/**
 * Persistence repository for purchase orders.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository

public class PurchaseOrderRepository {

    private final DSLContext dsl;

    public PurchaseOrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(
            String orderId,
            String tenantId,
            String checkoutSessionId,
            String canonicalProductCode,
            String orderStatus,
            Long amountMinor,
            String currencyCode,
            String productId, String offeringId, long offeringVersion,
            String commercialPriceRef, long commercialPriceVersion) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(PURCHASE_ORDER)
                .columns(
                        PURCHASE_ORDER.ID,
                        PURCHASE_ORDER.TENANT_ID,
                        PURCHASE_ORDER.CHECKOUT_SESSION_ID,
                        PURCHASE_ORDER.CANONICAL_PRODUCT_CODE,
                        DSL.field("product_id", String.class), DSL.field("offering_id", String.class),
                        DSL.field("offering_version", Long.class), DSL.field("commercial_price_ref", String.class),
                        DSL.field("commercial_price_version", Long.class), DSL.field("amount_minor_snapshot", Long.class),
                        DSL.field("currency_code_snapshot", String.class),
                        PURCHASE_ORDER.ORDER_STATUS,
                        PURCHASE_ORDER.TOTAL_AMOUNT_MINOR,
                        PURCHASE_ORDER.CURRENCY_CODE,
                        PURCHASE_ORDER.CREATED_AT)
                .values(orderId, effectiveTenant, checkoutSessionId, canonicalProductCode, productId, offeringId,
                        offeringVersion, commercialPriceRef, commercialPriceVersion, amountMinor, currencyCode,
                        orderStatus, amountMinor, currencyCode, now)
                .execute();
    }

    public List<PurchaseOrderRecord> findRecentByTenant(String tenantId, int limit) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.select()
                .from(PURCHASE_ORDER)
                .where(PURCHASE_ORDER.TENANT_ID.eq(effectiveTenant))
                .orderBy(PURCHASE_ORDER.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    public long sumConfirmedRevenueMinor(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        Long sum = dsl.select(org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(PURCHASE_ORDER.TOTAL_AMOUNT_MINOR), 0L))
                .from(PURCHASE_ORDER)
                .where(PURCHASE_ORDER.TENANT_ID.eq(effectiveTenant))
                .and(PURCHASE_ORDER.ORDER_STATUS.ne("CANCELLED"))
                .fetchOne(0, Long.class);
        return sum != null ? sum : 0L;
    }

    public Optional<PurchaseOrderRecord> findById(String id) {
        Record record = dsl.select()
                .from(PURCHASE_ORDER)
                .where(PURCHASE_ORDER.ID.eq(id))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<PurchaseOrderRecord> findByCheckoutSessionId(String checkoutSessionId) {
        Record record = dsl.select()
                .from(PURCHASE_ORDER)
                .where(PURCHASE_ORDER.CHECKOUT_SESSION_ID.eq(checkoutSessionId))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    private PurchaseOrderRecord mapRecord(Record r) {
        return new PurchaseOrderRecord(
                r.get(PURCHASE_ORDER.ID, String.class),
                r.get(PURCHASE_ORDER.CHECKOUT_SESSION_ID, String.class),
                r.get(PURCHASE_ORDER.CANONICAL_PRODUCT_CODE, String.class),
                r.get(PURCHASE_ORDER.ORDER_STATUS, String.class),
                r.get(PURCHASE_ORDER.TOTAL_AMOUNT_MINOR, Long.class),
                r.get(PURCHASE_ORDER.CURRENCY_CODE, String.class), r.get("offering_id", String.class),
                r.get("offering_version", Long.class), r.get("commercial_price_ref", String.class),
                r.get("commercial_price_version", Long.class));
    }

    private static Condition tenantPredicate() {
        return PURCHASE_ORDER.TENANT_ID.eq(TenantGuard.requireTenantId());
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
            String currencyCode,
            String offeringId, long offeringVersion, String commercialPriceRef, long commercialPriceVersion
    ) {}
}
