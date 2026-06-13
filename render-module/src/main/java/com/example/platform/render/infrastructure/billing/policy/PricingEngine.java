package com.example.platform.render.infrastructure.billing.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine for calculating render costs with pricing rules.
 * 
 * <p>Supports:
 * <ul>
 *   <li>Base price per provider</li>
 *   <li>Per-second compute cost</li>
 *   <li>GPU multiplier</li>
 *   <li>Effect cost multiplier</li>
 *   <li>Tier-based discounts</li>
 *   <li>Promotional overrides</li>
 * </ul>
 */
@Service
public class PricingEngine {

    private static final Logger log = LoggerFactory.getLogger(PricingEngine.class);

    private final Map<String, ProviderPricing> providerPricing = new ConcurrentHashMap<>();
    private final Map<String, TierDiscount> tierDiscounts = new ConcurrentHashMap<>();
    private final Map<String, PromotionalOverride> promotionalOverrides = new ConcurrentHashMap<>();

    public PricingEngine() {
        initializeDefaultPricing();
    }

    /**
     * Calculate the cost for a render operation.
     */
    public PricingResult calculate(PricingRequest request) {
        // Get provider pricing
        ProviderPricing pricing = providerPricing.getOrDefault(
                request.providerKey(),
                getDefaultPricing(request.providerKey())
        );

        // Calculate base compute cost
        double computeCost = pricing.basePricePerSecond() * request.durationSeconds();

        // Apply GPU multiplier
        if (request.useGpu()) {
            computeCost *= pricing.gpuMultiplier();
        }

        // Apply effect multiplier
        if (request.effectCount() > 0) {
            computeCost *= (1.0 + (request.effectCount() * pricing.effectCostMultiplier()));
        }

        // Apply preset multiplier
        double presetMultiplier = pricing.presetMultipliers()
                .getOrDefault(request.preset(), 1.0);
        computeCost *= presetMultiplier;

        // Calculate storage cost
        double storageCost = pricing.storageCostPerGb() * (request.outputSizeBytes() / (1024.0 * 1024 * 1024));

        // Calculate API cost
        double apiCost = pricing.apiCallCost();

        // Subtotal before discounts
        double subtotal = computeCost + storageCost + apiCost;

        // Apply tier discount
        double tierDiscount = 0;
        if (request.tier() != null) {
            TierDiscount discount = tierDiscounts.get(request.tier());
            if (discount != null) {
                tierDiscount = subtotal * (discount.discountPercent() / 100.0);
            }
        }

        // Apply promotional override
        double promoDiscount = 0;
        String promoCode = null;
        if (request.promoCode() != null) {
            PromotionalOverride promo = promotionalOverrides.get(request.promoCode());
            if (promo != null && promo.isValid()) {
                promoDiscount = subtotal * (promo.discountPercent() / 100.0);
                promoCode = promo.code();
            }
        }

        // Calculate final price
        double finalPrice = Math.max(0, subtotal - tierDiscount - promoDiscount);

        // Build cost breakdown
        CostBreakdown breakdown = new CostBreakdown(
                computeCost,
                storageCost,
                apiCost,
                subtotal,
                tierDiscount,
                promoDiscount,
                finalPrice,
                pricing.currency()
        );

        log.debug("Pricing calculated: provider={} duration={}s gpu={} final=${}",
                request.providerKey(), request.durationSeconds(), request.useGpu(),
                String.format("%.4f", finalPrice));

        return new PricingResult(
                finalPrice,
                pricing.currency(),
                breakdown,
                request.tier(),
                promoCode,
                request.providerKey(),
                request.preset()
        );
    }

    /**
     * Register provider pricing.
     */
    public void registerProviderPricing(ProviderPricing pricing) {
        providerPricing.put(pricing.providerKey(), pricing);
    }

    /**
     * Register tier discount.
     */
    public void registerTierDiscount(TierDiscount discount) {
        tierDiscounts.put(discount.tier(), discount);
    }

    /**
     * Register promotional override.
     */
    public void registerPromotionalOverride(PromotionalOverride promo) {
        promotionalOverrides.put(promo.code(), promo);
    }

    // ---------------------------------------------------------------------------
    // Default Pricing Initialization
    // ---------------------------------------------------------------------------

    private void initializeDefaultPricing() {
        // FFmpeg pricing
        registerProviderPricing(new ProviderPricing(
                "ffmpeg",
                0.05,  // base price per second
                2.0,   // GPU multiplier
                0.1,   // effect cost multiplier
                0.001, // storage cost per GB
                0.01,  // API call cost
                "USD",
                Map.of(
                        "default_720p", 0.6,
                        "default_1080p", 1.0,
                        "4k_2160p", 3.5,
                        "pro_1080p", 1.2
                )
        ));

        // GStreamer pricing
        registerProviderPricing(new ProviderPricing(
                "gstreamer",
                0.04,
                2.0,
                0.1,
                0.001,
                0.01,
                "USD",
                Map.of("default", 0.7)
        ));

        // Default tier discounts
        registerTierDiscount(new TierDiscount("FREE", 0));
        registerTierDiscount(new TierDiscount("STARTER", 10));
        registerTierDiscount(new TierDiscount("PRO", 20));
        registerTierDiscount(new TierDiscount("ENTERPRISE", 30));
    }

    private ProviderPricing getDefaultPricing(String providerKey) {
        return new ProviderPricing(
                providerKey,
                0.05, 1.0, 0.1, 0.001, 0.01, "USD", Map.of()
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    /**
     * Pricing configuration for a provider.
     */
    public record ProviderPricing(
            String providerKey,
            double basePricePerSecond,
            double gpuMultiplier,
            double effectCostMultiplier,
            double storageCostPerGb,
            double apiCallCost,
            String currency,
            Map<String, Double> presetMultipliers
    ) {}

    /**
     * Tier-based discount.
     */
    public record TierDiscount(
            String tier,
            double discountPercent
    ) {}

    /**
     * Promotional override.
     */
    public record PromotionalOverride(
            String code,
            String description,
            double discountPercent,
            Instant validFrom,
            Instant validTo,
            int maxUses,
            int currentUses
    ) {
        public boolean isValid() {
            Instant now = Instant.now();
            return (validFrom == null || validFrom.isBefore(now))
                    && (validTo == null || validTo.isAfter(now))
                    && (maxUses <= 0 || currentUses < maxUses);
        }
    }

    /**
     * Pricing calculation request.
     */
    public record PricingRequest(
            String providerKey,
            String preset,
            String tier,
            long durationSeconds,
            boolean useGpu,
            int effectCount,
            long outputSizeBytes,
            String promoCode
    ) {}

    /**
     * Pricing calculation result.
     */
    public record PricingResult(
            double finalPrice,
            String currency,
            CostBreakdown breakdown,
            String tierApplied,
            String promoCodeApplied,
            String providerKey,
            String preset
    ) {}

    /**
     * Detailed cost breakdown.
     */
    public record CostBreakdown(
            double computeCost,
            double storageCost,
            double apiCost,
            double subtotal,
            double tierDiscount,
            double promoDiscount,
            double finalPrice,
            String currency
    ) {
        public String formatSummary() {
            return String.format(
                    "Compute: $%.4f + Storage: $%.4f + API: $%.4f = $%.4f - Tier: $%.4f - Promo: $%.4f = $%.4f",
                    computeCost, storageCost, apiCost, subtotal,
                    tierDiscount, promoDiscount, finalPrice
            );
        }
    }
}
