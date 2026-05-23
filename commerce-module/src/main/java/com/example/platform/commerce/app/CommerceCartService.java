package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.*;
import com.example.platform.shared.Ids;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CommerceCartService {

    private final CommerceCatalogService catalogService;
    private final Map<String, CommerceCart> carts = new ConcurrentHashMap<>();

    public CommerceCartService(CommerceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public CommerceCart createCart(String tenantId, String userId) {
        Instant now = Instant.now();
        CommerceCart cart = new CommerceCart(Ids.newId("cart"), tenantId, userId, List.of(), now, now);
        carts.put(cart.cartId(), cart);
        return cart;
    }

    public CommerceCart getCart(String cartId) {
        CommerceCart cart = carts.get(cartId);
        if (cart == null) {
            throw new IllegalArgumentException("Cart not found: " + cartId);
        }
        return cart;
    }

    public CommerceCart addLine(String cartId, String productCode, int quantity) {
        catalogService.requireProduct(productCode);
        CommerceCart cart = getCart(cartId);
        List<CartLineItem> lines = new ArrayList<>(cart.lines());
        boolean merged = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).productCode().equals(productCode)) {
                lines.set(i, new CartLineItem(productCode, lines.get(i).quantity() + quantity));
                merged = true;
                break;
            }
        }
        if (!merged) {
            lines.add(new CartLineItem(productCode, quantity));
        }
        CommerceCart updated = new CommerceCart(
                cart.cartId(), cart.tenantId(), cart.userId(), List.copyOf(lines),
                cart.createdAt(), Instant.now());
        carts.put(cartId, updated);
        return updated;
    }

    public CommerceCart removeLine(String cartId, String productCode) {
        CommerceCart cart = getCart(cartId);
        List<CartLineItem> lines = cart.lines().stream()
                .filter(l -> !l.productCode().equals(productCode))
                .toList();
        CommerceCart updated = new CommerceCart(
                cart.cartId(), cart.tenantId(), cart.userId(), lines,
                cart.createdAt(), Instant.now());
        carts.put(cartId, updated);
        return updated;
    }

    public long cartTotalMinor(String cartId) {
        CommerceCart cart = getCart(cartId);
        long total = 0L;
        for (CartLineItem line : cart.lines()) {
            CanonicalProduct product = catalogService.requireProduct(line.productCode());
            total += product.priceMinor() * line.quantity();
        }
        return total;
    }

    public List<CanonicalProduct> resolveLines(String cartId) {
        CommerceCart cart = getCart(cartId);
        List<CanonicalProduct> products = new ArrayList<>();
        for (CartLineItem line : cart.lines()) {
            CanonicalProduct product = catalogService.requireProduct(line.productCode());
            for (int i = 0; i < line.quantity(); i++) {
                products.add(product);
            }
        }
        return products;
    }
}
