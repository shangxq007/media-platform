package com.example.platform.commerce.infrastructure;

import com.example.platform.commerce.domain.CheckoutSession;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.TenantGuard;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import com.example.platform.commerce.domain.AuthorityReference;
import com.example.platform.shared.commercial.Money;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.CheckoutSession.CHECKOUT_SESSION;


@Repository

public class CheckoutSessionRepository {

    private final DSLContext dsl;

    public CheckoutSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CheckoutSession save(CheckoutSession session, String userId, String cartId) {
        TenantGuard.assertSameTenantIfContextPresent(session.tenantId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(CHECKOUT_SESSION)
                .columns(
                        CHECKOUT_SESSION.ID,
                        CHECKOUT_SESSION.CHECKOUT_SESSION_CODE,
                        CHECKOUT_SESSION.TENANT_ID,
                        CHECKOUT_SESSION.PRODUCT_ID,
                        DSL.field("canonical_product_code", String.class),
                        DSL.field("offering_id", String.class), DSL.field("offering_version", Long.class),
                        DSL.field("commercial_price_ref", String.class), DSL.field("commercial_price_version", Long.class),
                        DSL.field("amount_minor_snapshot", Long.class), DSL.field("currency_code_snapshot", String.class),
                        CHECKOUT_SESSION.PROVIDER_CODE,
                        CHECKOUT_SESSION.SESSION_STATUS,
                        CHECKOUT_SESSION.SUCCESS_URL,
                        CHECKOUT_SESSION.CANCEL_URL,
                        CHECKOUT_SESSION.USER_ID,
                        CHECKOUT_SESSION.CART_ID,
                        CHECKOUT_SESSION.CREATED_AT)
                .values(
                        session.checkoutSessionId(),
                        session.checkoutSessionId(),
                        session.tenantId(),
                        session.productId(), session.canonicalProductCode(),
                        session.offeringId(), session.offeringVersion(), session.commercialPriceReference().key(),
                        session.commercialPriceReference().version(), session.amountSnapshot().amountMinor(), session.amountSnapshot().currency(),
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
                .from(CHECKOUT_SESSION)
                .where(CHECKOUT_SESSION.ID.eq(id))
                .and(tenantPredicate())
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<CheckoutSession> findByIdForTenant(String id, String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        Record record = dsl.select()
                .from(CHECKOUT_SESSION)
                .where(CHECKOUT_SESSION.ID.eq(id))
                .and(CHECKOUT_SESSION.TENANT_ID.eq(effectiveTenant))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<SessionMetadata> findMetadata(String id) {
        Record record = dsl.select(CHECKOUT_SESSION.USER_ID, CHECKOUT_SESSION.CART_ID, CHECKOUT_SESSION.TENANT_ID)
                .from(CHECKOUT_SESSION)
                .where(CHECKOUT_SESSION.ID.eq(id))
                .and(tenantPredicate())
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionMetadata(
                record.get(CHECKOUT_SESSION.USER_ID, String.class),
                record.get(CHECKOUT_SESSION.CART_ID, String.class),
                record.get(CHECKOUT_SESSION.TENANT_ID, String.class)));
    }

    public void updateStatus(String id, String status) {
        dsl.update(CHECKOUT_SESSION)
                .set(CHECKOUT_SESSION.SESSION_STATUS, status)
                .where(CHECKOUT_SESSION.ID.eq(id))
                .and(tenantPredicate())
                .execute();
    }

    public long countActiveForTenant(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.fetchCount(
                dsl.selectFrom(CHECKOUT_SESSION)
                        .where(CHECKOUT_SESSION.TENANT_ID.eq(effectiveTenant))
                        .and(CHECKOUT_SESSION.SESSION_STATUS.eq("PENDING")));
    }

    public List<String> listActiveSessionIds(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        return dsl.select(CHECKOUT_SESSION.ID)
                .from(CHECKOUT_SESSION)
                .where(CHECKOUT_SESSION.TENANT_ID.eq(effectiveTenant))
                .and(CHECKOUT_SESSION.SESSION_STATUS.eq("PENDING"))
                .fetch(CHECKOUT_SESSION.ID);
    }

    private CheckoutSession mapRecord(Record record) {
        return new CheckoutSession(
                record.get(CHECKOUT_SESSION.ID, String.class),
                record.get(CHECKOUT_SESSION.TENANT_ID, String.class),
                record.get("canonical_product_code", String.class),
                record.get("product_id", String.class), record.get("offering_id", String.class),
                record.get("offering_version", Long.class),
                new AuthorityReference(record.get("commercial_price_ref", String.class), record.get("commercial_price_version", Long.class)),
                new Money(record.get("amount_minor_snapshot", Long.class), record.get("currency_code_snapshot", String.class)),
                record.get(CHECKOUT_SESSION.SUCCESS_URL, String.class),
                record.get(CHECKOUT_SESSION.PROVIDER_CODE, String.class));
    }

    private static Condition tenantPredicate() {
        return CHECKOUT_SESSION.TENANT_ID.eq(TenantGuard.requireTenantId());
    }

    public record SessionMetadata(String userId, String cartId, String tenantId) {}
}
