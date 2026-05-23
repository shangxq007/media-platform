package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.ProductLineType;
import com.example.platform.commerce.domain.PurchaseMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommerceCatalogServiceTest {

    private final CommerceCatalogService catalog = new CommerceCatalogService();

    @Test
    void listsBaseAddonAndCreditProducts() {
        assertTrue(catalog.listProducts().size() >= 8);
        assertTrue(catalog.findProduct("pro_monthly").isPresent());
        assertTrue(catalog.findProduct("addon_gpu_monthly").isPresent());
        assertTrue(catalog.findProduct("credit_pack_50").isPresent());
    }

    @Test
    void proMonthlyIsBaseSubscription() {
        var product = catalog.requireProduct("pro_monthly");
        assertEquals(ProductLineType.BASE_SUBSCRIPTION, product.lineType());
        assertEquals(PurchaseMode.SUBSCRIPTION, product.purchaseMode());
        assertEquals("PRO", product.tierKey());
    }

    @Test
    void enterpriseRestrictedToAllowlistedTenants() {
        var product = catalog.requireProduct("enterprise_monthly");
        assertFalse(catalog.isAvailableForTenant(product, "random-tenant"));
        assertTrue(catalog.isAvailableForTenant(product, "tenant-1"));
    }
}
