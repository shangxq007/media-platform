package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.domain.RateUsageCommand;
import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.infrastructure.BillableUsageJdbcRepository;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class CommercialPricingRatingAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant EFFECTIVE = Instant.parse("2026-08-29T08:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static PricingRuleService pricing;
    private static RatingEngine rating;
    private static CommercialPricingJdbcRepository pricingRepository;
    private static BillableUsageJdbcRepository billableRepository;
    private static RatedUsageJdbcRepository ratedRepository;
    private static TestRatingAudit audit;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        pricing = context.getBean(PricingRuleService.class);
        rating = context.getBean(RatingEngine.class);
        pricingRepository = context.getBean(CommercialPricingJdbcRepository.class);
        billableRepository = context.getBean(BillableUsageJdbcRepository.class);
        ratedRepository = context.getBean(RatedUsageJdbcRepository.class);
        audit = context.getBean(TestRatingAudit.class);
        jdbc.execute("DROP TABLE IF EXISTS rated_usage_record, discount_policy, custom_pricing_rule, pricing_rule CASCADE");
        jdbc.execute("DROP TABLE IF EXISTS billable_usage CASCADE");
        jdbc.execute("""
                CREATE TABLE pricing_rule (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    rule_key varchar(128) not null, rule_version bigint not null,
                    name varchar(255) not null, description text,
                    pricing_model varchar(32) not null, meter_key varchar(128) not null,
                    unit_price_minor bigint not null, currency_code varchar(3) not null,
                    tier_config text not null, status varchar(32) not null,
                    effective_from timestamptz not null, effective_to timestamptz,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    primary key (tenant_id, id),
                    unique (tenant_id, rule_key, rule_version)
                )
                """);
        jdbc.execute("""
                CREATE TABLE custom_pricing_rule (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    workspace_id varchar(64), meter_key varchar(128) not null,
                    rule_version bigint not null, override_price_minor bigint,
                    currency_code varchar(3) not null,
                    discount_numerator bigint, discount_denominator bigint,
                    effective_from timestamptz not null, effective_to timestamptz,
                    status varchar(32) not null, created_at timestamptz not null,
                    primary key (tenant_id, id),
                    unique (tenant_id, workspace_id, meter_key, rule_version)
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX uq_custom_pricing_scope_test ON custom_pricing_rule(
                    tenant_id, coalesce(workspace_id, ''), meter_key, rule_version)
                """);
        jdbc.execute("""
                CREATE TABLE discount_policy (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    policy_key varchar(128) not null, rule_version bigint not null,
                    meter_key varchar(128) not null, currency_code varchar(3) not null,
                    name varchar(255) not null, description text, discount_type varchar(32) not null,
                    discount_numerator bigint not null, discount_denominator bigint not null,
                    flat_amount_minor bigint not null, conditions text, status varchar(32) not null,
                    effective_from timestamptz not null, effective_to timestamptz,
                    created_at timestamptz not null, primary key (tenant_id, id),
                    unique (tenant_id, policy_key, rule_version)
                )
                """);
        jdbc.execute("""
                CREATE TABLE billable_usage (
                    billable_usage_id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    observed_usage_id varchar(64) not null, observed_dimension varchar(64) not null,
                    observed_quantity_base_units bigint not null, observed_quantity_unit varchar(32) not null,
                    billable_meter varchar(128) not null, billable_dimension varchar(64) not null,
                    billable_quantity_base_units bigint not null, billable_quantity_unit varchar(32) not null,
                    metering_rule_id varchar(128) not null, metering_rule_version varchar(64) not null,
                    transformation_kind varchar(64) not null, transformation_details text not null,
                    source_observation_timestamp timestamptz not null, metered_at timestamptz not null,
                    idempotency_key varchar(255) not null, trace_id varchar(128) not null,
                    provenance_reference varchar(512) not null,
                    unique (tenant_id, idempotency_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE rated_usage_record (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    billable_usage_id varchar(64) not null, pricing_rule_id varchar(64) not null,
                    pricing_rule_version bigint not null, quantity_base_units bigint not null,
                    rated_amount_minor bigint not null, currency_code varchar(3) not null,
                    rating_details text not null, rated_at timestamptz not null,
                    trace_id varchar(128) not null, idempotency_key varchar(255) not null,
                    payload_fingerprint varchar(64) not null,
                    unique (tenant_id, idempotency_key),
                    unique (tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version)
                )
                """);
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) context.close();
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_rated_insert ON rated_usage_record");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_rated_insert_for_test()");
        jdbc.execute("TRUNCATE rated_usage_record, billable_usage, discount_policy, custom_pricing_rule, pricing_rule");
        audit.reset();
        pricing.saveRule(rule("GLOBAL", "rule-api", "api", 1, 5, "USD",
                EFFECTIVE.minusSeconds(60), null));
    }

    @Test
    void pricingPersistsExactVersionEffectivityAndTenantOverride() {
        pricing.saveOverride(new com.example.platform.billing.domain.CustomPricingRule(
                "override-a", "tenant-a", "workspace-a", "api", 1,
                new Money(4, "USD"), 1, 4, EFFECTIVE.minusSeconds(30), null,
                "ACTIVE", EFFECTIVE.minusSeconds(30)));

        PricingRuleService.PricingPreviewResult global = pricing.previewPricing(
                new PricingQuoteCommand("tenant-b", null, "api", 3,
                        "rule-api", 1, EFFECTIVE, Map.of()));
        PricingRuleService.PricingPreviewResult overridden = pricing.previewPricing(
                new PricingQuoteCommand("tenant-a", "workspace-a", "api", 3,
                        "rule-api", 1, EFFECTIVE, Map.of()));

        assertEquals(new Money(15, "USD"), global.amount());
        assertEquals(new Money(9, "USD"), overridden.amount());
        assertEquals(1L, overridden.pricingRuleVersion());
        assertEquals("override-a", overridden.overrideRuleId());
        assertEquals(rule("GLOBAL", "rule-api", "api", 1, 5, "USD",
                        EFFECTIVE.minusSeconds(60), null),
                pricingRepository.findRule("GLOBAL", "rule-api", 1).orElseThrow());
    }

    @Test
    void unknownMeterRuleVersionAndInactiveIntervalFailClosed() {
        assertThrows(IllegalStateException.class, () -> pricing.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "unknown", 1,
                        "rule-api", 1, EFFECTIVE, Map.of())));
        assertThrows(IllegalStateException.class, () -> pricing.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "api", 1,
                        "missing", 1, EFFECTIVE, Map.of())));
        assertThrows(IllegalStateException.class, () -> pricing.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "api", 1,
                        "rule-api", 2, EFFECTIVE, Map.of())));
        assertThrows(IllegalStateException.class, () -> pricing.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "api", 1,
                        "rule-api", 1, EFFECTIVE.minusSeconds(120), Map.of())));
    }

    @Test
    void discountPersistsExactTenantMeterVersionCurrencyAndEffectivity() {
        var discount = pricing.createDiscountPolicy("tenant-a", "discount-api", 3,
                "api", "USD", "API discount", "", "PERCENTAGE",
                1, 5, 0, Map.of(), EFFECTIVE.minusSeconds(1), null);

        var quote = pricing.previewPricing(new PricingQuoteCommand(
                "tenant-a", null, "api", 3, "rule-api", 1, EFFECTIVE, Map.of()));

        assertEquals(new Money(12, "USD"), quote.amount());
        assertEquals(discount,
                pricingRepository.findDiscount("tenant-a", "discount-api", 3).orElseThrow());
    }

    @Test
    void tierConfigurationPersistsExactlyAndUsesCumulativeThresholds() {
        PricingRule tiered = new PricingRule("tier-rule", "GLOBAL", "tiered-api", 4,
                "Tiered API", "", PricingModel.USAGE_BASED, "tier-api",
                new Money(0, "USD"), List.of(
                        new PricingTier(100, 10, 0), new PricingTier(1_000, 5, 0)),
                "ACTIVE", EFFECTIVE.minusSeconds(1), null,
                EFFECTIVE.minusSeconds(1), EFFECTIVE.minusSeconds(1));
        pricing.saveRule(tiered);

        assertEquals(tiered,
                pricingRepository.findRule("GLOBAL", "tiered-api", 4).orElseThrow());
        assertEquals(new Money(1_250, "USD"), pricing.previewPricing(new PricingQuoteCommand(
                "tenant-a", null, "tier-api", 150, "tiered-api", 4, EFFECTIVE, Map.of())).amount());
    }

    @Test
    void ratingExactReplayPersistsCompleteProvenance() {
        BillableUsage usage = billableRepository.append(usage("tenant-a", "bill-1", 3));
        RateUsageCommand command = new RateUsageCommand(
                usage, "rule-api", 1, "rate-command-1", EFFECTIVE, "trace-rate");

        RatedUsageRecord first = rating.rate(command);
        RatedUsageRecord replay = rating.rate(command);

        assertEquals(first, replay);
        assertEquals(new Money(15, "USD"), first.amount());
        assertEquals(3L, first.quantityBaseUnits());
        assertEquals(1L, first.pricingRuleVersion());
        assertEquals("bill-1", first.billableUsageId());
        assertEquals(1L, ratedRepository.countByTenant("tenant-a"));
        assertEquals(1, audit.calls);
    }

    @Test
    void ratingMismatchTenantIsolationCurrencyAndOverflowFailClosed() {
        BillableUsage usage = billableRepository.append(usage("tenant-a", "bill-2", 3));
        RateUsageCommand first = new RateUsageCommand(
                usage, "rule-api", 1, "same-key", EFFECTIVE, "trace-rate");
        rating.rate(first);
        assertThrows(IllegalStateException.class, () -> rating.rate(new RateUsageCommand(
                usage("tenant-a", "different", 3), "rule-api", 1,
                "same-key", EFFECTIVE, "trace-rate")));
        assertEquals(0L, ratedRepository.countByTenant("tenant-b"));

        pricing.saveRule(rule("GLOBAL", "eur-rule", "eur", 1, 2, "EUR",
                EFFECTIVE.minusSeconds(1), null));
        BillableUsage wrongMeter = billableRepository.append(usage("tenant-a", "bill-eur", 1));
        assertThrows(IllegalStateException.class, () -> rating.rate(new RateUsageCommand(
                wrongMeter, "eur-rule", 1, "currency-mismatch", EFFECTIVE, "trace")));

        pricing.saveRule(rule("GLOBAL", "overflow-rule", "api", 1, Long.MAX_VALUE, "USD",
                EFFECTIVE.minusSeconds(1), null));
        assertThrows(ArithmeticException.class, () -> rating.rate(new RateUsageCommand(
                usage, "overflow-rule", 1, "overflow", EFFECTIVE, "trace")));
    }

    @Test
    void concurrentDuplicateRatingCreatesOneRecord() throws Exception {
        BillableUsage usage = billableRepository.append(usage("tenant-a", "bill-concurrent", 3));
        RateUsageCommand command = new RateUsageCommand(
                usage, "rule-api", 1, "concurrent-rate", EFFECTIVE, "trace-rate");

        List<RatedUsageRecord> results = concurrently(12, () -> rating.rate(command));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(1L, ratedRepository.countByTenant("tenant-a"));
        assertEquals(1, audit.calls);
    }

    @Test
    void ratingPersistenceAndAuditFailuresRollBack() {
        BillableUsage usage = billableRepository.append(usage("tenant-a", "bill-rollback", 3));
        jdbc.execute("""
                CREATE FUNCTION fail_rated_insert_for_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'rated persistence failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_rated_insert BEFORE INSERT ON rated_usage_record
                FOR EACH ROW EXECUTE FUNCTION fail_rated_insert_for_test()
                """);
        assertThrows(RuntimeException.class, () -> rating.rate(new RateUsageCommand(
                usage, "rule-api", 1, "persist-fail", EFFECTIVE, "trace")));
        assertEquals(0, audit.calls);
        jdbc.execute("DROP TRIGGER fail_rated_insert ON rated_usage_record");
        jdbc.execute("DROP FUNCTION fail_rated_insert_for_test()");

        audit.fail = true;
        assertThrows(IllegalStateException.class, () -> rating.rate(new RateUsageCommand(
                usage, "rule-api", 1, "audit-fail", EFFECTIVE, "trace")));
        assertEquals(0L, ratedRepository.countByTenant("tenant-a"));
    }

    private static PricingRule rule(String tenantId, String id, String meter, long version,
                                    long unitPriceMinor, String currency,
                                    Instant effectiveFrom, Instant effectiveTo) {
        return new PricingRule(id, tenantId, id, version, id, "", PricingModel.USAGE_BASED,
                meter, new Money(unitPriceMinor, currency), List.of(), "ACTIVE",
                effectiveFrom, effectiveTo, EFFECTIVE.minusSeconds(60), EFFECTIVE.minusSeconds(60));
    }

    private static BillableUsage usage(String tenant, String id, long quantity) {
        UsageQuantity exact = new UsageQuantity(quantity, UsageUnit.COUNT);
        return new BillableUsage(id, tenant, new CanonicalActorRef("user-1", "USER"),
                "observed-" + id, UsageDimension.REQUEST, exact, "api",
                UsageDimension.REQUEST, exact, "meter-rule", "v1",
                MeteringTransformationKind.IDENTITY, "identity", EFFECTIVE, EFFECTIVE,
                "bill-idem-" + id, "trace-bill", "observed-" + id);
    }

    private static <T> List<T> concurrently(int workers, java.util.concurrent.Callable<T> action)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        static DataSource dataSource;

        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate() { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean CommercialPricingJdbcRepository pricingRepository(JdbcTemplate jdbc) {
            return new CommercialPricingJdbcRepository(jdbc);
        }
        @Bean BillableUsageJdbcRepository billableRepository(JdbcTemplate jdbc) {
            return new BillableUsageJdbcRepository(jdbc);
        }
        @Bean RatedUsageJdbcRepository ratedRepository(JdbcTemplate jdbc) {
            return new RatedUsageJdbcRepository(jdbc);
        }
        @Bean TestRatingAudit audit() { return new TestRatingAudit(); }
        @Bean PricingRuleService pricing(CommercialPricingJdbcRepository repository) {
            return new PricingRuleService(repository);
        }
        @Bean RatingEngine rating(CommercialPricingJdbcRepository pricingRepository,
                                  RatedUsageJdbcRepository ratedRepository,
                                  TestRatingAudit audit) {
            return new RatingEngine(pricingRepository, ratedRepository, audit);
        }
    }

    static class TestRatingAudit implements RatedUsageAuditPort {
        volatile int calls;
        volatile boolean fail;

        @Override
        public synchronized void record(RatedUsageRecord record) {
            if (fail) throw new IllegalStateException("rating audit failure");
            calls++;
        }

        void reset() { calls = 0; fail = false; }
    }
}
