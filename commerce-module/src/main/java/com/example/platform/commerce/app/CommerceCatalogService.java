package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.CanonicalProduct;
import com.example.platform.commerce.domain.ProductLineType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommerceCatalogService {

    private final List<CanonicalProduct> products = List.of(
            CanonicalProduct.baseSubscription(
                    "basic_monthly",
                    "basic_monthly",
                    "FREE",
                    "basic_features",
                    "basic_quota",
                    2999L,
                    "Basic Monthly"),
            CanonicalProduct.baseSubscription(
                    "pro_monthly",
                    "pro_monthly",
                    "PRO",
                    "default_features",
                    "pro_quota",
                    9999L,
                    "Pro Monthly"),
            CanonicalProduct.baseSubscription(
                    "team_monthly",
                    "team_monthly",
                    "TEAM",
                    "team_features",
                    "team_quota",
                    29999L,
                    "Team Monthly"),
            CanonicalProduct.baseSubscription(
                    "enterprise_monthly",
                    "enterprise_monthly",
                    "ENTERPRISE",
                    "enterprise_features",
                    "enterprise_quota",
                    99999L,
                    "Enterprise Monthly"),
            CanonicalProduct.addOnSubscription(
                    "addon_gpu_monthly",
                    "addon_gpu_monthly",
                    "gpu_render",
                    "addon_gpu_features",
                    "pro_quota",
                    4999L,
                    "GPU Render Add-on"),
            CanonicalProduct.addOnSubscription(
                    "addon_ai_monthly",
                    "addon_ai_monthly",
                    "ai_editing",
                    "addon_ai_features",
                    "pro_quota",
                    2999L,
                    "AI Editing Add-on"),
            CanonicalProduct.creditPack(
                    "credit_pack_50",
                    5000L,
                    5000L,
                    "Credit Pack ($50)"),
            CanonicalProduct.creditPack(
                    "credit_pack_200",
                    20000L,
                    18000L,
                    "Credit Pack ($200)"),
            CanonicalProduct.seatPack(
                    "seat_pack_5",
                    5,
                    "render.minutes",
                    1999L,
                    "5 Additional Seats")
    );

    public List<CanonicalProduct> listProducts() {
        return products;
    }

    public Optional<CanonicalProduct> findProduct(String productCode) {
        return products.stream()
                .filter(p -> p.productCode().equals(productCode))
                .findFirst();
    }

    public CanonicalProduct requireProduct(String productCode) {
        return findProduct(productCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productCode));
    }

    public boolean isAvailableForTenant(CanonicalProduct product, String tenantId) {
        if (product.lineType() == ProductLineType.BASE_SUBSCRIPTION
                && "enterprise_monthly".equals(product.productCode())) {
            return "tenant-1".equals(tenantId) || "tenant-prod".equals(tenantId);
        }
        return true;
    }
}
