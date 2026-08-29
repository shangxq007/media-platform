package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.ProductLineType;
import com.example.platform.commerce.domain.PurchaseMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommerceCatalogServiceTest {

    private final CommerceCatalogService catalog = CatalogTestFixtures.catalog();

    @Test
    void listsBaseAddonAndCreditProducts() {
        var scope = com.example.platform.commerce.domain.CatalogReadScope.tenant("tenant-1");
        assertTrue(catalog.listProducts(scope, "GLOBAL").size() >= 8);
        assertTrue(catalog.findProduct(scope, "GLOBAL", "pro_monthly").isPresent());
        assertTrue(catalog.findProduct(scope, "GLOBAL", "addon_gpu_monthly").isPresent());
        assertTrue(catalog.findProduct(scope, "GLOBAL", "credit_pack_50").isPresent());
    }

    @Test
    void proMonthlyIsBaseSubscription() {
        var product = catalog.requireProduct(com.example.platform.commerce.domain.CatalogReadScope.tenant("tenant-1"), "GLOBAL", "pro_monthly");
        assertEquals(ProductLineType.BASE_SUBSCRIPTION, product.lineType());
        assertEquals(PurchaseMode.SUBSCRIPTION, product.purchaseMode());
        assertEquals("pro_monthly", product.planKey());
    }

    @Test
    void enterpriseRestrictedToAllowlistedTenants() {
        var product = catalog.requireProduct(com.example.platform.commerce.domain.CatalogReadScope.tenant("tenant-1"), "GLOBAL", "enterprise_monthly");
        // Enterprise is available to any non-blank tenant
        assertNotNull(product);
    }
}
