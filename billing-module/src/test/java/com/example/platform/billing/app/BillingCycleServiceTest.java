package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.SubscriptionCommand;
import com.example.platform.billing.domain.SubscriptionCommandType;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.billing.infrastructure.BillableUsageJdbcRepository;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.billing.usage.MeterUsageCommand;
import com.example.platform.billing.usage.MeteringRule;
import com.example.platform.billing.usage.MeteringRuleRegistry;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.usage.RuntimeOutcome;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageProvenance;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;

import static org.junit.jupiter.api.Assertions.*;

class BillingCycleServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private BillingCycleService cycleService;
    private SubscriptionBillingService subscriptionBillingService;
    private UsageMeteringService usageMeteringService;
    private PricingRuleService pricingRuleService;
    private ObservedRuntimeUsageJdbcRepository observations;
    private MeteringRuleRegistry meteringRules;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("DROP TABLE IF EXISTS credit_wallet_command, credit_transaction, credit_reservation, credit_wallet CASCADE");
        jdbc.execute("CREATE TABLE IF NOT EXISTS subscription_plan (id varchar(64) primary key, plan_key varchar(128) not null unique, name varchar(255) not null, description text, billing_interval varchar(32), base_price_minor bigint not null, currency_code varchar(8) not null, included_quota text, status varchar(32) not null, created_at timestamp not null, updated_at timestamp not null)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS subscription_contract (id varchar(64) primary key, tenant_id varchar(64) not null, subject_type varchar(32) not null, subject_id varchar(128) not null, canonical_product_code varchar(128) not null, contract_role varchar(32) not null, contract_state varchar(32) not null, period_start_at timestamp not null, period_end_at timestamp, created_at timestamp not null, updated_at timestamp not null, plan_key varchar(128) not null, included_quota_used text, version bigint not null)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS subscription_command (id varchar(64) primary key, tenant_id varchar(64) not null, principal_type varchar(32) not null, principal_id varchar(128) not null, idempotency_key varchar(255) not null, command_type varchar(32) not null, payload_fingerprint text not null, result_snapshot text, actor varchar(128) not null, reason varchar(512) not null, trace_id varchar(128) not null, created_at timestamptz not null, completed_at timestamptz, unique (tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS billing_ledger_entry (id varchar(64) primary key, tenant_id varchar(64) not null, principal_type varchar(32) not null, principal_id varchar(128) not null, workspace_id varchar(64), entry_type varchar(32) not null, amount_minor bigint not null, currency_code varchar(3) not null, reference_type varchar(64) not null, reference_id varchar(128) not null, description text not null, idempotency_key varchar(255) not null, payload_fingerprint varchar(64) not null, created_at timestamptz not null, unique (tenant_id, idempotency_key), unique (tenant_id, reference_type, reference_id, entry_type))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_wallet (id varchar(64) not null, tenant_id varchar(64) not null, principal_type varchar(32) not null, principal_id varchar(128) not null, workspace_id varchar(64), balance_minor bigint not null, currency_code varchar(3) not null, status varchar(32) not null, version bigint not null, created_at timestamptz not null, updated_at timestamptz not null, primary key (tenant_id, id), unique (tenant_id, principal_type, principal_id, workspace_id, currency_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_reservation (id varchar(64) not null, tenant_id varchar(64) not null, wallet_id varchar(64) not null, amount_minor bigint not null, currency_code varchar(3) not null, status varchar(32) not null, version bigint not null, reference_type varchar(64) not null, reference_id varchar(128) not null, created_at timestamptz not null, updated_at timestamptz not null, primary key (tenant_id, id), foreign key (tenant_id, wallet_id) references credit_wallet(tenant_id, id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_transaction (id varchar(64) primary key, tenant_id varchar(64) not null, wallet_id varchar(64) not null, reservation_id varchar(64), transaction_type varchar(32) not null, amount_minor bigint not null, currency_code varchar(3) not null, balance_after_minor bigint not null, reference_type varchar(64) not null, reference_id varchar(128) not null, description text not null, idempotency_key varchar(255) not null, payload_fingerprint varchar(64) not null, created_at timestamptz not null, unique (tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS credit_wallet_command (id varchar(64) primary key, tenant_id varchar(64) not null, wallet_id varchar(64) not null, idempotency_key varchar(255) not null, command_type varchar(32) not null, payload_fingerprint varchar(64) not null, result_balance_minor bigint not null, result_currency varchar(3) not null, result_wallet_version bigint not null, result_reservation_id varchar(64), result_reservation_status varchar(32), actor varchar(128) not null, reason varchar(512) not null, trace_id varchar(128) not null, created_at timestamptz not null, unique (tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS observed_runtime_usage (observed_usage_id varchar(64) primary key, tenant_id varchar(64) not null, project_id varchar(64), principal_type varchar(32) not null, principal_id varchar(128) not null, operation_ref varchar(128) not null, attempt_ref varchar(128) not null, execution_ref varchar(128), provider_ref varchar(128) not null, capability varchar(128) not null, dimension varchar(64) not null, quantity_base_units bigint not null, quantity_unit varchar(32) not null, operation_outcome varchar(32) not null, occurred_at timestamptz not null, observed_at timestamptz not null, recorded_at timestamptz not null, provenance varchar(32) not null, source varchar(128) not null, source_reference varchar(255) not null, trace_id varchar(128) not null, idempotency_key varchar(255) not null, unique (tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS billable_usage (billable_usage_id varchar(64) primary key, tenant_id varchar(64) not null, principal_type varchar(32) not null, principal_id varchar(128) not null, observed_usage_id varchar(64) not null, observed_dimension varchar(64) not null, observed_quantity_base_units bigint not null, observed_quantity_unit varchar(32) not null, billable_meter varchar(128) not null, billable_dimension varchar(64) not null, billable_quantity_base_units bigint not null, billable_quantity_unit varchar(32) not null, metering_rule_id varchar(128) not null, metering_rule_version varchar(64) not null, transformation_kind varchar(64) not null, transformation_details text not null, source_observation_timestamp timestamptz not null, metered_at timestamptz not null, idempotency_key varchar(255) not null, trace_id varchar(128) not null, provenance_reference varchar(512) not null, unique (tenant_id, idempotency_key), unique (tenant_id, observed_usage_id, metering_rule_id, metering_rule_version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS pricing_rule (id varchar(64) not null, tenant_id varchar(64) not null, rule_key varchar(128) not null, rule_version bigint not null, name varchar(255) not null, description text, pricing_model varchar(32) not null, meter_key varchar(128) not null, unit_price_minor bigint not null, currency_code varchar(3) not null, tier_config text not null, status varchar(32) not null, effective_from timestamptz not null, effective_to timestamptz, created_at timestamptz not null, updated_at timestamptz not null, primary key (tenant_id, id), unique (tenant_id, rule_key, rule_version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS custom_pricing_rule (id varchar(64) not null, tenant_id varchar(64) not null, workspace_id varchar(64), meter_key varchar(128) not null, rule_version bigint not null, override_price_minor bigint, currency_code varchar(3) not null, discount_numerator bigint, discount_denominator bigint, effective_from timestamptz not null, effective_to timestamptz, status varchar(32) not null, created_at timestamptz not null, primary key (tenant_id, id), unique (tenant_id, workspace_id, meter_key, rule_version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS discount_policy (id varchar(64) not null, tenant_id varchar(64) not null, policy_key varchar(128) not null, rule_version bigint not null, meter_key varchar(128) not null, currency_code varchar(3) not null, name varchar(255) not null, description text, discount_type varchar(32) not null, discount_numerator bigint not null, discount_denominator bigint not null, flat_amount_minor bigint not null, conditions text, status varchar(32) not null, effective_from timestamptz not null, effective_to timestamptz, created_at timestamptz not null, primary key (tenant_id, id), unique (tenant_id, policy_key, rule_version))");
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE credit_wallet_command, credit_transaction, credit_reservation, credit_wallet CASCADE");
        jdbc.execute("TRUNCATE TABLE billing_ledger_entry CASCADE");
        jdbc.execute("TRUNCATE TABLE subscription_contract CASCADE");
        jdbc.execute("TRUNCATE TABLE subscription_command CASCADE");
        jdbc.execute("TRUNCATE TABLE subscription_plan CASCADE");
        jdbc.execute("TRUNCATE TABLE billable_usage, observed_runtime_usage");
        jdbc.execute("TRUNCATE TABLE custom_pricing_rule, pricing_rule, discount_policy");

        subscriptionBillingService = new SubscriptionBillingService(new SubscriptionJdbcRepository(jdbc));
        observations = new ObservedRuntimeUsageJdbcRepository(jdbc);
        meteringRules = new MeteringRuleRegistry();
        meteringRules.register(new MeteringRule(
                "cycle-duration", "v1", UsageDimension.DURATION, UsageUnit.SECONDS,
                "DURATION", UsageDimension.DURATION, UsageUnit.SECONDS,
                1, 1, 1, MeteringTransformationKind.IDENTITY, "identity seconds"));
        usageMeteringService = new UsageMeteringService(
                observations, new BillableUsageJdbcRepository(jdbc), meteringRules, usage -> {});
        pricingRuleService = new PricingRuleService(new CommercialPricingJdbcRepository(jdbc));
        cycleService = new BillingCycleService(
                usageMeteringService,
                subscriptionBillingService,
                pricingRuleService,
                new BillingLedgerService(Optional.of(new BillingLedgerJdbcRepository(jdbc))),
                new CreditWalletService(new CreditWalletJdbcRepository(jdbc)));

        pricingRuleService.createPricingRule(
                "render_overage", "Render", "", PricingModel.USAGE_BASED,
                "DURATION", 10L, "USD", null, null, null);
        subscriptionBillingService.createPlan(
                "pro_monthly", "Pro", "", "MONTHLY", 9999, "USD",
                Map.of("DURATION", 100L));
        PrincipalRef principal = PrincipalRef.tenantScoped("t1", PrincipalType.USER, "u1");
        subscriptionBillingService.execute(new SubscriptionCommand(
                SubscriptionCommandType.CREATE, principal, "cycle-contract", "pro_monthly",
                "pro_monthly", 30, SubscriptionContractRole.BASE, 0, "cycle-create",
                "test", "cycle", "trace-cycle", Instant.now()));
    }

    @Test
    void chargesOverageBeyondIncludedQuota() {
        meterUsage(150, "cycle-150");

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(500L, result.totalChargeMinor());
        assertTrue(result.lines().stream().anyMatch(l -> "OVERAGE".equals(l.disposition())));
    }

    @Test
    void noChargeWhenWithinIncludedQuota() {
        meterUsage(50, "cycle-50");

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(0L, result.totalChargeMinor());
    }

    private void meterUsage(long seconds, String key) {
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        ObservedRuntimeUsage observation = observations.append(ObservedRuntimeUsage.observe(
                "t1", "project-1", new CanonicalActorRef("u1", "USER"),
                OperationRef.of("operation-" + key, "attempt-1"), "execution-" + key,
                new ProviderRef("provider-1"), "render", UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(seconds, UsageUnit.SECONDS),
                RuntimeOutcome.SUCCEEDED, now, now, now, UsageProvenance.REPORTED,
                "test", "source-" + key, "trace-" + key, key));
        usageMeteringService.meter(new MeterUsageCommand(
                "t1", observation.observedUsageId(), "cycle-duration", "v1",
                now, "meter-" + key));
    }
}
