package com.example.platform.commerce.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.commerce.domain.CheckoutSession;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.TenantGuard;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class CheckoutSessionRepository {

    private final DSLContext dsl;

    public CheckoutSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CheckoutSession save(CheckoutSession session, String userId, String cartId) {
        TenantGuard.assertSameTenantIfContextPresent(session.tenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(table("checkout_session"))
                .columns(
                        field("id"),
                        field("checkout_session_code"),
                        field("tenant_id"),
                        field("product_id"),
                        field("provider_code"),
                        field("session_status"),
                        field("success_url"),
                        field("cancel_url"),
                        field("user_id"),
                        field("cart_id"),
                        field("created_at"))
                .values(
                        session.checkoutSessionId(),
                        session.checkoutSessionId(),
                        session.tenantId(),
                        session.canonicalProductCode(),
                        session.providerHint(),
                        "PENDING",
                        session.redirectUrl(),
                        "",
                        userId,
                        cartId,
                        now)
                .execute();
        return session;
    }

    public Optional<CheckoutSession> findById(String id) {
        Record record = dsl.select()
                .from(table("checkout_session"))
                .where(field("id").eq(id))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    /** Loads by id; enforces tenant match when {@link com.example.platform.shared.web.TenantContext} is set. */
    public Optional<CheckoutSession> findByIdUnchecked(String id) {
        Record record = dsl.select()
                .from(table("checkout_session"))
                .where(field("id").eq(id))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        String resourceTenant = record.get(field("tenant_id"), String.class);
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.isBlank()) {
            TenantGuard.assertSameTenant(resourceTenant);
        }
        return Optional.of(mapRecord(record));
    }

    public Optional<CheckoutSession> findByIdForTenant(String id, String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        Record record = dsl.select()
                .from(table("checkout_session"))
                .where(field("id").eq(id))
                .and(field("tenant_id").eq(effectiveTenant))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<SessionMetadata> findMetadata(String id) {
        Record record = dsl.select(field("user_id"), field("cart_id"), field("tenant_id"))
                .from(table("checkout_session"))
                .where(field("id").eq(id))
                .and(tenantPredicate())
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionMetadata(
                record.get(field("user_id"), String.class),
                record.get(field("cart_id"), String.class),
                record.get(field("tenant_id"), String.class)));
    }

    public void updateStatus(String id, String status) {
        dsl.update(table("checkout_session"))
                .set(field("session_status"), status)
                .where(field("id").eq(id))
                .and(tenantPredicate())
                .execute();
    }

    public long countActiveForTenant(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.fetchCount(
                dsl.selectFrom(table("checkout_session"))
                        .where(field("tenant_id").eq(effectiveTenant))
                        .and(field("session_status").eq("PENDING")));
    }

    public List<String> listActiveSessionIds(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.select(field("id"))
                .from(table("checkout_session"))
                .where(field("tenant_id").eq(effectiveTenant))
                .and(field("session_status").eq("PENDING"))
                .fetch(field("id", String.class));
    }

    private CheckoutSession mapRecord(Record record) {
        return new CheckoutSession(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("product_id"), String.class),
                record.get(field("success_url"), String.class),
                record.get(field("provider_code"), String.class));
    }

    private static Condition tenantPredicate() {
        return field("tenant_id").eq(TenantGuard.requireTenantId());
    }

    public record SessionMetadata(String userId, String cartId, String tenantId) {}
}
