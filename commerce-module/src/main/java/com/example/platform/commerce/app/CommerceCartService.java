package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.*;
import com.example.platform.commerce.infrastructure.CommerceCartRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommerceCartService {

    private final CommerceCatalogService catalogService;
    private final Optional<CommerceCartRepository> cartRepository;
    private final Map<String, CommerceCart> inMemoryCarts = new ConcurrentHashMap<>();

    public CommerceCartService(CommerceCatalogService catalogService) {
        this(catalogService, null);
    }

    @Autowired
    public CommerceCartService(
            CommerceCatalogService catalogService,
            @Autowired(required = false) CommerceCartRepository cartRepository) {
        this.catalogService = catalogService;
        this.cartRepository = Optional.ofNullable(cartRepository);
    }

    private boolean dbBacked() {
        return cartRepository.isPresent();
    }

    public CommerceCart createCart(String tenantId, String userId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        Instant now = Instant.now();
        CommerceCart cart = new CommerceCart(Ids.newId("cart"), effectiveTenant, userId, List.of(), now, now);
        if (dbBacked()) {
            return cartRepository.get().save(cart);
        }
        inMemoryCarts.put(cart.cartId(), cart);
        return cart;
    }

    public CommerceCart getCart(String cartId) {
        if (dbBacked()) {
            return cartRepository.get().findById(cartId).orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        }
        CommerceCart cart = inMemoryCarts.get(cartId);
        if (cart == null) {
            throw new IllegalArgumentException("Cart not found: " + cartId);
        }
        return cart;
    }

    public CommerceCart addLine(String cartId, String productCode, int quantity) {
        CommerceCart cart = getCart(cartId);
        CommercialOffering offering = catalogService.requireOffering(CatalogReadScope.tenant(cart.tenantId()), "GLOBAL", productCode, Instant.now());
        TenantGuard.assertSameTenantIfContextPresent(cart.tenantId());
        List<CartLineItem> lines = new ArrayList<>(cart.lines());
        boolean merged = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).productCode().equals(productCode)) {
                CartLineItem old = lines.get(i);
                lines.set(i, new CartLineItem(productCode, old.quantity() + quantity, old.productId(), old.offeringId(),
                        old.offeringVersion(), old.commercialPriceReference(), old.amountMinorSnapshot(), old.currencyCodeSnapshot()));
                merged = true;
                break;
            }
        }
        if (!merged) {
            lines.add(new CartLineItem(productCode, quantity, offering.productId(), offering.offeringId(), offering.offeringVersion(),
                    offering.commercialPriceReference(), offering.priceSnapshot().amountMinor(), offering.priceSnapshot().currency()));
        }
        return persistCart(new CommerceCart(
                cart.cartId(), cart.tenantId(), cart.userId(), List.copyOf(lines), cart.createdAt(), Instant.now()));
    }

    public CommerceCart removeLine(String cartId, String productCode) {
        CommerceCart cart = getCart(cartId);
        TenantGuard.assertSameTenantIfContextPresent(cart.tenantId());
        List<CartLineItem> lines = cart.lines().stream()
                .filter(l -> !l.productCode().equals(productCode))
                .toList();
        return persistCart(new CommerceCart(
                cart.cartId(), cart.tenantId(), cart.userId(), lines, cart.createdAt(), Instant.now()));
    }

    public long cartTotalMinor(String cartId) {
        CommerceCart cart = getCart(cartId);
        long total = 0L;
        for (CartLineItem line : cart.lines()) {
            total = Math.addExact(total, Math.multiplyExact(line.amountMinorSnapshot(), line.quantity()));
        }
        return total;
    }

    public List<CanonicalProduct> resolveLines(String cartId) {
        CommerceCart cart = getCart(cartId);
        List<CanonicalProduct> products = new ArrayList<>();
        for (CartLineItem line : cart.lines()) {
            CanonicalProduct product = CommerceCatalogService.project(catalogService.requireHistorical(
                    CatalogReadScope.tenant(cart.tenantId()), line.offeringId(), line.offeringVersion()));
            for (int i = 0; i < line.quantity(); i++) {
                products.add(product);
            }
        }
        return products;
    }

    private CommerceCart persistCart(CommerceCart cart) {
        if (dbBacked()) {
            return cartRepository.get().save(cart);
        }
        inMemoryCarts.put(cart.cartId(), cart);
        return cart;
    }
}
