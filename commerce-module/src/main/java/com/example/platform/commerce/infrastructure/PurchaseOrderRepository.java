package com.example.platform.commerce.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.web.TenantGuard;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

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
            String currencyCode) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(table("purchase_order"))
                .columns(
                        field("id"),
                        field("tenant_id"),
                        field("checkout_session_id"),
                        field("canonical_product_code"),
                        field("order_status"),
                        field("total_amount_minor"),
                        field("currency_code"),
                        field("created_at"))
                .values(orderId, effectiveTenant, checkoutSessionId, canonicalProductCode, orderStatus, amountMinor, currencyCode, now)
                .execute();
    }

    public List<PurchaseOrderRecord> findRecentByTenant(String tenantId, int limit) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.select()
                .from(table("purchase_order"))
                .where(field("tenant_id").eq(effectiveTenant))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    public long sumConfirmedRevenueMinor(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        Long sum = dsl.select(org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(field("total_amount_minor", Long.class)), 0L))
                .from(table("purchase_order"))
                .where(field("tenant_id").eq(effectiveTenant))
                .and(field("order_status").ne("CANCELLED"))
                .fetchOne(0, Long.class);
        return sum != null ? sum : 0L;
    }

    public Optional<PurchaseOrderRecord> findById(String id) {
        Record record = dsl.select()
                .from(table("purchase_order"))
                .where(field("id").eq(id))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<PurchaseOrderRecord> findByCheckoutSessionId(String checkoutSessionId) {
        Record record = dsl.select()
                .from(table("purchase_order"))
                .where(field("checkout_session_id").eq(checkoutSessionId))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    private PurchaseOrderRecord mapRecord(Record r) {
        return new PurchaseOrderRecord(
                r.get(field("id"), String.class),
                r.get(field("checkout_session_id"), String.class),
                r.get(field("canonical_product_code"), String.class),
                r.get(field("order_status"), String.class),
                r.get(field("total_amount_minor"), Long.class),
                r.get(field("currency_code"), String.class));
    }

    private static Condition tenantPredicate() {
        return field("tenant_id").eq(TenantGuard.requireTenantId());
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
