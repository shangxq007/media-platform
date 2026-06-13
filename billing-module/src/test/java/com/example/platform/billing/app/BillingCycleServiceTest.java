package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BillingCycleServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private BillingCycleService cycleService;
    private SubscriptionBillingService subscriptionBillingService;
    private UsageMeteringService usageMeteringService;
    private PricingRuleService pricingRuleService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS subscription_plan (id varchar(64) primary key, plan_key varchar(128) not null unique, name varchar(255) not null, description text, billing_interval varchar(32), base_price_minor bigint not null, currency_code varchar(8) not null, included_quota text, status varchar(32) not null, created_at timestamp not null, updated_at timestamp not null)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS subscription_contract (id varchar(64) primary key, tenant_id varchar(64), subject_type varchar(32) not null, subject_id varchar(128) not null, canonical_product_code varchar(128), provider_code varchar(64), external_contract_ref varchar(255), contract_state varchar(32) not null, period_start_at timestamp, period_end_at timestamp, created_at timestamp not null, plan_key varchar(128), included_quota_used text)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS billing_ledger_entry (id varchar(64) primary key, tenant_id varchar(64) not null, workspace_id varchar(64), user_id varchar(128), entry_type varchar(32) not null, amount_minor bigint not null, currency_code varchar(8) not null, reference_type varchar(64), reference_id varchar(128), description text, created_at timestamp not null)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_wallet (id varchar(64) primary key, tenant_id varchar(64) not null, workspace_id varchar(64), user_id varchar(128), balance_minor bigint not null default 0, currency_code varchar(8) not null, status varchar(32) not null, created_at timestamp not null, updated_at timestamp not null)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_transaction (id varchar(64) primary key, wallet_id varchar(64) not null, transaction_type varchar(32) not null, amount_minor bigint not null, balance_after_minor bigint not null, reference_type varchar(64), reference_id varchar(128), description text, created_at timestamp not null)");
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE credit_transaction CASCADE");
        jdbc.execute("TRUNCATE TABLE credit_wallet CASCADE");
        jdbc.execute("TRUNCATE TABLE billing_ledger_entry CASCADE");
        jdbc.execute("TRUNCATE TABLE subscription_contract CASCADE");
        jdbc.execute("TRUNCATE TABLE subscription_plan CASCADE");

        subscriptionBillingService = new SubscriptionBillingService(Optional.of(new SubscriptionJdbcRepository(jdbc)));
        usageMeteringService = new UsageMeteringService();
        pricingRuleService = new PricingRuleService();
        cycleService = new BillingCycleService(
                usageMeteringService,
                subscriptionBillingService,
                pricingRuleService,
                new BillingLedgerService(Optional.of(new BillingLedgerJdbcRepository(jdbc))),
                new CreditWalletService(Optional.of(new CreditWalletJdbcRepository(jdbc))));

        pricingRuleService.createPricingRule(
                "render_minutes_overage", "Render", "", PricingModel.USAGE_BASED,
                "render.minutes", 10L, "USD", null, null, null);
        subscriptionBillingService.createPlan(
                "pro_monthly", "Pro", "", "MONTHLY", 9999, "USD",
                Map.of("render.minutes", 100L));
        subscriptionBillingService.createSubscription(
                "t1", "u1", "pro_monthly", "pro_monthly", 30, SubscriptionContractRole.BASE);
    }

    @Test
    void chargesOverageBeyondIncludedQuota() {
        usageMeteringService.recordUsage(
                "t1", null, "u1", "render.minutes", 150, "minute", null, null);

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(500L, result.totalChargeMinor());
        assertTrue(result.lines().stream().anyMatch(l -> "OVERAGE".equals(l.disposition())));
    }

    @Test
    void noChargeWhenWithinIncludedQuota() {
        usageMeteringService.recordUsage(
                "t1", null, "u1", "render.minutes", 50, "minute", null, null);

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(0L, result.totalChargeMinor());
    }
}
