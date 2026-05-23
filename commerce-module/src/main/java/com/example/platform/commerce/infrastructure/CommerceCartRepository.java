package com.example.platform.commerce.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.commerce.domain.CartLineItem;
import com.example.platform.commerce.domain.CommerceCart;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantGuard;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class CommerceCartRepository {

    private final DSLContext dsl;

    public CommerceCartRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CommerceCart save(CommerceCart cart) {
        TenantGuard.assertSameTenantIfContextPresent(cart.tenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = dsl.update(table("commerce_cart"))
                .set(field("user_id"), cart.userId())
                .set(field("updated_at"), now)
                .where(field("id").eq(cart.cartId()))
                .and(tenantPredicate())
                .execute();
        if (updated == 0) {
            dsl.insertInto(table("commerce_cart"))
                    .columns(field("id"), field("tenant_id"), field("user_id"), field("created_at"), field("updated_at"))
                    .values(cart.cartId(), cart.tenantId(), cart.userId(), now, now)
                    .execute();
        }
        dsl.deleteFrom(table("commerce_cart_line"))
                .where(field("cart_id").eq(cart.cartId()))
                .execute();
        for (CartLineItem line : cart.lines()) {
            dsl.insertInto(table("commerce_cart_line"))
                    .columns(field("id"), field("cart_id"), field("product_code"), field("quantity"), field("created_at"))
                    .values(Ids.newId("cline"), cart.cartId(), line.productCode(), line.quantity(), now)
                    .execute();
        }
        return cart;
    }

    public Optional<CommerceCart> findById(String cartId) {
        Record header = dsl.select()
                .from(table("commerce_cart"))
                .where(field("id").eq(cartId))
                .and(tenantPredicate())
                .fetchOne();
        if (header == null) {
            return Optional.empty();
        }
        List<CartLineItem> lines = dsl.select(field("product_code"), field("quantity"))
                .from(table("commerce_cart_line"))
                .where(field("cart_id").eq(cartId))
                .fetch(r -> new CartLineItem(r.get(field("product_code"), String.class), r.get(field("quantity"), Integer.class)));
        return Optional.of(new CommerceCart(
                cartId,
                header.get(field("tenant_id"), String.class),
                header.get(field("user_id"), String.class),
                List.copyOf(lines),
                toInstant(header.get(field("created_at"), OffsetDateTime.class)),
                toInstant(header.get(field("updated_at"), OffsetDateTime.class))));
    }

    private static org.jooq.Condition tenantPredicate() {
        return field("tenant_id").eq(TenantGuard.requireTenantId());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value != null ? value.toInstant() : Instant.now();
    }
}
