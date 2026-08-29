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
     * @param billableUsage the metered, lineage-bearing usage to consume
     */
    void consume(BillableUsage billableUsage);
}
