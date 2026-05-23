package com.example.platform.commerce.domain;

/**
 * Platform-owned catalog SKU. Provider product IDs map here; never the reverse.
 */
public record CanonicalProduct(
        String productCode,
        PurchaseMode purchaseMode,
        ProductLineType lineType,
        String featureBundleCode,
        String quotaProfileCode,
        /** Billing {@code SubscriptionPlan.planKey}; required for subscription line types. */
        String planKey,
        /** Entitlement tier (FREE/PRO/TEAM/ENTERPRISE) for {@link ProductLineType#BASE_SUBSCRIPTION}. */
        String tierKey,
        /** Entitlement bundle grant key for {@link ProductLineType#ADD_ON_SUBSCRIPTION}. */
        String bundleKey,
        /** Credits to add for {@link ProductLineType#CREDIT_PACK} (minor units). */
        Long creditAmountMinor,
        /** Seats to add for {@link ProductLineType#SEAT_PACK}. */
        Integer includedSeats,
        /** Workspace pool feature key for seat packs (e.g. {@code render.minutes}). */
        String seatFeatureKey,
        long priceMinor,
        String currencyCode,
        String displayName) {

    /** Backward-compatible view for APIs that still expose purchase mode as a string. */
    public String purchaseModeName() {
        return purchaseMode.name().toLowerCase();
    }

    public static CanonicalProduct baseSubscription(
            String productCode,
            String planKey,
            String tierKey,
            String featureBundleCode,
            String quotaProfileCode,
            long priceMinor,
            String displayName) {
        return new CanonicalProduct(
                productCode,
                PurchaseMode.SUBSCRIPTION,
                ProductLineType.BASE_SUBSCRIPTION,
                featureBundleCode,
                quotaProfileCode,
                planKey,
                tierKey,
                null,
                null,
                null,
                null,
                priceMinor,
                "USD",
                displayName);
    }

    public static CanonicalProduct addOnSubscription(
            String productCode,
            String planKey,
            String bundleKey,
            String featureBundleCode,
            String quotaProfileCode,
            long priceMinor,
            String displayName) {
        return new CanonicalProduct(
                productCode,
                PurchaseMode.SUBSCRIPTION,
                ProductLineType.ADD_ON_SUBSCRIPTION,
                featureBundleCode,
                quotaProfileCode,
                planKey,
                null,
                bundleKey,
                null,
                null,
                null,
                priceMinor,
                "USD",
                displayName);
    }

    public static CanonicalProduct creditPack(
            String productCode,
            long creditAmountMinor,
            long priceMinor,
            String displayName) {
        return new CanonicalProduct(
                productCode,
                PurchaseMode.CREDIT_PACK,
                ProductLineType.CREDIT_PACK,
                null,
                null,
                null,
                null,
                null,
                creditAmountMinor,
                null,
                null,
                priceMinor,
                "USD",
                displayName);
    }

    public static CanonicalProduct seatPack(
            String productCode,
            int includedSeats,
            String seatFeatureKey,
            long priceMinor,
            String displayName) {
        return new CanonicalProduct(
                productCode,
                PurchaseMode.SEAT_PACK,
                ProductLineType.SEAT_PACK,
                null,
                null,
                null,
                null,
                null,
                null,
                includedSeats,
                seatFeatureKey,
                priceMinor,
                "USD",
                displayName);
    }
}
