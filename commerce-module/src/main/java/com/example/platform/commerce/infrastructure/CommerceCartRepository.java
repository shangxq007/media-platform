package com.example.platform.commerce.infrastructure;

import com.example.platform.commerce.domain.CartLineItem;
import com.example.platform.commerce.domain.CommerceCart;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantGuard;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import com.example.platform.commerce.domain.AuthorityReference;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.CommerceCart.COMMERCE_CART;
import static com.example.platform.typedschema.jooq.generated.tables.CommerceCartLine.COMMERCE_CART_LINE;


@Repository

public class CommerceCartRepository {

    private final DSLContext dsl;

    public CommerceCartRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CommerceCart save(CommerceCart cart) {
        TenantGuard.assertSameTenantIfContextPresent(cart.tenantId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = dsl.update(COMMERCE_CART)
                .set(COMMERCE_CART.USER_ID, cart.userId())
                .set(COMMERCE_CART.UPDATED_AT, now)
                .where(COMMERCE_CART.ID.eq(cart.cartId()))
                .and(tenantPredicate())
                .execute();
        if (updated == 0) {
            dsl.insertInto(COMMERCE_CART)
                    .columns(COMMERCE_CART.ID, COMMERCE_CART.TENANT_ID, COMMERCE_CART.USER_ID, COMMERCE_CART.CREATED_AT, COMMERCE_CART.UPDATED_AT)
                    .values(cart.cartId(), cart.tenantId(), cart.userId(), now, now)
                    .execute();
        }
        dsl.deleteFrom(COMMERCE_CART_LINE)
                .where(COMMERCE_CART_LINE.CART_ID.eq(cart.cartId()))
                .execute();
        for (CartLineItem line : cart.lines()) {
            dsl.insertInto(COMMERCE_CART_LINE)
                    .columns(COMMERCE_CART_LINE.ID, COMMERCE_CART_LINE.CART_ID, COMMERCE_CART_LINE.PRODUCT_CODE,
                            DSL.field("product_id", String.class), DSL.field("offering_id", String.class),
                            DSL.field("offering_version", Long.class), DSL.field("commercial_price_ref", String.class),
                            DSL.field("commercial_price_version", Long.class), DSL.field("amount_minor_snapshot", Long.class),
                            DSL.field("currency_code_snapshot", String.class), COMMERCE_CART_LINE.QUANTITY, COMMERCE_CART_LINE.CREATED_AT)
                    .values(Ids.newId("cline"), cart.cartId(), line.productCode(), line.productId(), line.offeringId(),
                            line.offeringVersion(), line.commercialPriceReference().key(), line.commercialPriceReference().version(),
                            line.amountMinorSnapshot(), line.currencyCodeSnapshot(), line.quantity(), now)
                    .execute();
        }
        return cart;
    }

    public Optional<CommerceCart> findById(String cartId) {
        Record header = dsl.select()
                .from(COMMERCE_CART)
                .where(COMMERCE_CART.ID.eq(cartId))
                .and(tenantPredicate())
                .fetchOne();
        if (header == null) {
            return Optional.empty();
        }
        List<CartLineItem> lines = dsl.select()
                .from(COMMERCE_CART_LINE)
                .where(COMMERCE_CART_LINE.CART_ID.eq(cartId))
                .fetch(r -> new CartLineItem(r.get(COMMERCE_CART_LINE.PRODUCT_CODE, String.class),
                        r.get(COMMERCE_CART_LINE.QUANTITY, Integer.class), r.get("product_id", String.class),
                        r.get("offering_id", String.class), r.get("offering_version", Long.class),
                        new AuthorityReference(r.get("commercial_price_ref", String.class), r.get("commercial_price_version", Long.class)),
                        r.get("amount_minor_snapshot", Long.class), r.get("currency_code_snapshot", String.class)));
        return Optional.of(new CommerceCart(
                cartId,
                header.get(COMMERCE_CART.TENANT_ID, String.class),
                header.get(COMMERCE_CART.USER_ID, String.class),
                List.copyOf(lines),
                toInstant(header.get(COMMERCE_CART.CREATED_AT, LocalDateTime.class)),
                toInstant(header.get(COMMERCE_CART.UPDATED_AT, LocalDateTime.class))));
    }

    private static org.jooq.Condition tenantPredicate() {
        return COMMERCE_CART.TENANT_ID.eq(TenantGuard.requireTenantId());
    }

    private static Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : Instant.now();
    }
}
