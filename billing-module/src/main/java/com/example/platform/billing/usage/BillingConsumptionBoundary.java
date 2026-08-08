package com.example.platform.billing.usage;

/**
 * Billing consumption boundary: BILLING_CONSUMES_USAGE.
 *
 * <p>Canonical usage facts are consumed here by existing billing consumers
 * ({@code RatingEngine} / {@code BillingCycleService}) via the compatibility adapter.
 * Billing does NOT invent execution usage — it only projects and consumes canonical
 * facts emitted by the platform.</p>
 */
public interface BillingConsumptionBoundary {

    /**
     * Consume a canonical usage record by projecting it onto the existing billing
     * consumption path.
     *
     * @param canonical the canonical usage fact to consume
     */
    void consume(UsageRecord canonical);
}
