package com.example.platform.commerce.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.commerce.domain.*;
import com.example.platform.commerce.infrastructure.ProductCatalogJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class ProductCatalogAuthorityPostgresTest extends PostgresTestContainerSupport {
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ProductCatalogAuthority authority;

    @BeforeAll
    static void schema() {
        dataSource = createDataSource();
        Config.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authority = context.getBean(ProductCatalogAuthority.class);
        jdbc.execute("DROP TABLE IF EXISTS provider_product_mapping, product_catalog_command, commercial_offering, commerce_product CASCADE");
        jdbc.execute("""
                CREATE TABLE commerce_product (
                  id varchar(64) primary key, product_code varchar(128) not null unique,
                  product_line_type varchar(64) not null, display_name varchar(255) not null,
                  lifecycle_state varchar(16) not null, version bigint not null,
                  created_at timestamptz not null, updated_at timestamptz not null)
                """);
        jdbc.execute("""
                CREATE TABLE commercial_offering (
                  id varchar(64) primary key, product_id varchar(64) not null,
                  offering_key varchar(128) not null, offering_version bigint not null,
                  lifecycle_state varchar(16) not null, row_version bigint not null,
                  purchase_mode varchar(32) not null, tenant_scope varchar(64) not null,
                  market_scope varchar(32) not null, valid_from timestamptz not null, valid_to timestamptz,
                  entitlement_bundle_ref varchar(128), entitlement_bundle_version bigint,
                  quota_profile_ref varchar(128), quota_profile_version bigint,
                  subscription_plan_ref varchar(128), subscription_plan_version bigint,
                  commercial_price_ref varchar(128) not null, commercial_price_version bigint not null,
                  amount_minor_snapshot bigint not null, currency_code_snapshot varchar(3) not null,
                  credit_quantity_minor bigint, seat_quantity integer, seat_feature_key varchar(128),
                  created_at timestamptz not null, updated_at timestamptz not null,
                  unique(product_id, offering_key, offering_version))
                """);
        jdbc.execute("""
                CREATE TABLE product_catalog_command (
                  id varchar(64) primary key, catalog_scope varchar(64) not null,
                  actor_tenant_id varchar(64) not null, actor_principal_type varchar(32) not null,
                  actor_principal_id varchar(128) not null, command_type varchar(32) not null,
                  idempotency_key varchar(255) not null, payload_fingerprint varchar(64) not null,
                  product_id varchar(64), offering_id varchar(64), provider_mapping_id varchar(64),
                  result_state varchar(32) not null, result_version bigint not null,
                  source varchar(128) not null, reason varchar(512) not null, trace_id varchar(128) not null,
                  created_at timestamptz not null, unique(catalog_scope,idempotency_key))
                """);
        jdbc.execute("""
                CREATE TABLE provider_product_mapping (
                  id varchar(64) primary key, provider_code varchar(64) not null,
                  external_product_ref varchar(255) not null, external_price_ref varchar(255),
                  product_id varchar(64) not null, offering_id varchar(64) not null,
                  offering_version bigint not null, version bigint not null,
                  created_at timestamptz not null, updated_at timestamptz not null,
                  unique(provider_code,external_product_ref),
                  unique(provider_code,product_id,offering_id,offering_version))
                """);
    }

    @AfterAll static void close() { if (context != null) context.close(); closeDataSource(dataSource); }

    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_offering ON commercial_offering");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_catalog_test()");
        jdbc.execute("TRUNCATE provider_product_mapping, product_catalog_command, commercial_offering, commerce_product");
    }

    @Test void createExactReplayAndMismatch() {
        var command = create("tenant-a", "pro", "offer-pro", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-1");
        var first = authority.create(command);
        assertEquals(first, authority.create(command));
        assertEquals(1, count("commerce_product"));
        assertEquals(1, count("commercial_offering"));
        assertEquals(1, count("product_catalog_command"));
        assertThrows(IllegalStateException.class, () -> authority.create(create(
                "tenant-a", "different", "offer-other", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-1")));
    }

    @Test void stableProductOwnsMultipleOfferingVersionsAndMarkets() {
        var usV1 = authority.create(createVersion("pro", "offer-pro-us-v1", "pro-monthly", 1,
                "US", "create-pro-us-v1"));
        var euV2 = authority.create(createVersion("pro", "offer-pro-eu-v2", "pro-monthly", 2,
                "EU", "create-pro-eu-v2"));

        assertEquals(usV1.product(), euV2.product());
        assertEquals("prod-pro", euV2.offering().productId());
        assertEquals(1, count("commerce_product"));
        assertEquals(2, count("commercial_offering"));
        assertEquals(2, count("product_catalog_command"));
    }

    @Test void concurrentCreateAndLifecycleCasHaveOneWinner() throws Exception {
        var command = create("tenant-a", "pro", "offer-pro", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-race");
        var pool = Executors.newFixedThreadPool(2);
        var createStart = new CountDownLatch(1);
        var a = pool.submit(() -> run(createStart, () -> authority.create(command)));
        var b = pool.submit(() -> run(createStart, () -> authority.create(command)));
        createStart.countDown();
        assertTrue(a.get(10, TimeUnit.SECONDS));
        assertTrue(b.get(10, TimeUnit.SECONDS));
        var activate = lifecycle("offer-pro", 1, OfferingLifecycleState.ACTIVE, "activate-same");
        CountDownLatch gate = new CountDownLatch(1);
        a = pool.submit(() -> run(gate, () -> authority.transition(activate)));
        b = pool.submit(() -> run(gate, () -> authority.transition(new LifecycleOfferingCommand(
                activate.actor(), activate.offeringId(), activate.expectedVersion(), OfferingLifecycleState.ACTIVE,
                "activate-other", activate.source(), activate.reason(), "trace-other", activate.occurredAt()))));
        gate.countDown();
        int winners = (a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdownNow();
        assertEquals(1, winners);
    }

    @Test void lifecycleIsForwardOnlyAndRetiredVersionRemainsReadable() {
        var created = authority.create(create("tenant-a", "pro", "offer-pro", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-life"));
        authority.transitionProduct(productLifecycle(created.product().productId(), 1,
                ProductLifecycleState.ACTIVE, "activate-product-life"));
        var active = authority.transition(lifecycle(created.offering().offeringId(), 1, OfferingLifecycleState.ACTIVE, "activate"));
        var retired = authority.transition(lifecycle(active.offeringId(), 2, OfferingLifecycleState.RETIRED, "retire"));
        assertEquals(OfferingLifecycleState.RETIRED, retired.lifecycleState());
        assertEquals(retired, authority.findHistorical(CatalogReadScope.tenant("tenant-a"), retired.offeringId(), 1).orElseThrow());
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "US", "pro", NOW).isEmpty());
        assertThrows(IllegalStateException.class, () -> authority.transition(lifecycle(retired.offeringId(), 3, OfferingLifecycleState.ACTIVE, "regress")));
        assertEquals(1, count("commercial_offering"), "retirement must not hard-delete history");
    }

    @Test void offeringAndProductLifecyclesAreIndependentAndAllVersionsRemainReadable() {
        var v1 = authority.create(createVersion("pro", "offer-pro-v1", "pro-monthly", 1,
                "GLOBAL", "create-pro-v1"));
        var v2 = authority.create(createVersion("pro", "offer-pro-v2", "pro-monthly", 2,
                "GLOBAL", "create-pro-v2"));

        var productActive = authority.transitionProduct(productLifecycle(
                v1.product().productId(), 1, ProductLifecycleState.ACTIVE, "activate-product"));
        var v1Active = authority.transition(lifecycle(v1.offering().offeringId(), 1,
                OfferingLifecycleState.ACTIVE, "activate-v1"));
        var v2Active = authority.transition(lifecycle(v2.offering().offeringId(), 1,
                OfferingLifecycleState.ACTIVE, "activate-v2"));

        var v1Retired = authority.transition(lifecycle(v1Active.offeringId(), 2,
                OfferingLifecycleState.RETIRED, "retire-v1"));
        assertEquals(ProductLifecycleState.ACTIVE, authority.findProduct(
                CatalogReadScope.tenant("tenant-a"), productActive.productId()).orElseThrow().lifecycleState());
        assertEquals(2, authority.findProduct(
                CatalogReadScope.tenant("tenant-a"), productActive.productId()).orElseThrow().version());
        assertEquals(OfferingLifecycleState.ACTIVE,
                authority.findHistorical(CatalogReadScope.tenant("tenant-a"), v2Active.offeringId(), 2).orElseThrow().lifecycleState());
        assertEquals(OfferingLifecycleState.RETIRED,
                authority.findHistorical(CatalogReadScope.tenant("tenant-a"), v1Retired.offeringId(), 1).orElseThrow().lifecycleState());
        assertEquals(v2Active.offeringId(), authority.resolveForCheckout(
                CatalogReadScope.tenant("tenant-a"), "GLOBAL", "pro", NOW).orElseThrow().offeringId());

        var productRetired = authority.transitionProduct(productLifecycle(
                productActive.productId(), 2, ProductLifecycleState.RETIRED, "retire-product"));
        assertEquals(ProductLifecycleState.RETIRED, productRetired.lifecycleState());
        assertEquals(OfferingLifecycleState.ACTIVE,
                authority.findHistorical(CatalogReadScope.tenant("tenant-a"), v2Active.offeringId(), 2).orElseThrow().lifecycleState(),
                "explicit product retirement must not rewrite sibling offering history");
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "GLOBAL", "pro", NOW).isEmpty());
        assertEquals(2, count("commercial_offering"), "neither lifecycle authority may hard-delete versions");
    }

    @Test void productLifecycleUsesVersionedCasAndDoesNotMutateOfferings() throws Exception {
        var created = authority.create(createVersion("pro", "offer-pro", "pro-monthly", 1,
                "GLOBAL", "create-product-cas"));
        var activate = productLifecycle(created.product().productId(), 1,
                ProductLifecycleState.ACTIVE, "activate-product-a");
        var active = authority.transitionProduct(activate);
        assertEquals(active, authority.transitionProduct(activate));
        assertThrows(IllegalStateException.class, () -> authority.transitionProduct(new LifecycleProductCommand(
                activate.actor(), activate.productId(), 2, ProductLifecycleState.RETIRED,
                activate.idempotencyKey(), activate.source(), activate.reason(), activate.traceId(), activate.occurredAt())));

        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        var a = pool.submit(() -> run(gate, () -> authority.transitionProduct(productLifecycle(
                created.product().productId(), 2, ProductLifecycleState.RETIRED, "retire-product-a"))));
        var b = pool.submit(() -> run(gate, () -> authority.transitionProduct(new LifecycleProductCommand(
                activate.actor(), activate.productId(), 2, ProductLifecycleState.RETIRED,
                "retire-product-b", activate.source(), activate.reason(), "trace-product-b", activate.occurredAt()))));
        gate.countDown();
        int winners = (a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdownNow();

        assertEquals(1, winners);
        assertEquals(ProductLifecycleState.RETIRED,
                authority.findProduct(CatalogReadScope.tenant("tenant-a"),
                        created.product().productId()).orElseThrow().lifecycleState());
        assertEquals(3, authority.findProduct(CatalogReadScope.tenant("tenant-a"),
                created.product().productId()).orElseThrow().version());
        assertEquals(active, authority.transitionProduct(activate),
                "exact replay must return the original product lifecycle result after later transitions");
        assertEquals(OfferingLifecycleState.DRAFT,
                authority.findHistorical(CatalogReadScope.tenant("tenant-a"), created.offering().offeringId(), 1).orElseThrow().lifecycleState());
    }

    @Test void tenantMarketTimeAndLifecycleApplicabilityFailClosed() {
        var created = authority.create(create("tenant-a", "pro", "offer-pro", "tenant-a", "US", NOW.minusSeconds(5), NOW.plusSeconds(5), "create-scope"));
        authority.transitionProduct(productLifecycle(created.product().productId(), 1,
                ProductLifecycleState.ACTIVE, "activate-product-scope"));
        authority.transition(lifecycle(created.offering().offeringId(), 1, OfferingLifecycleState.ACTIVE, "activate-scope"));
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "US", "pro", NOW).isPresent());
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-b"), "US", "pro", NOW).isEmpty());
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "EU", "pro", NOW).isEmpty());
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "US", "pro", NOW.minusSeconds(10)).isEmpty());
        assertTrue(authority.resolveForCheckout(CatalogReadScope.tenant("tenant-a"), "US", "pro", NOW.plusSeconds(10)).isEmpty());
    }

    @Test void providerMappingIsSubordinateUniqueIdempotentAndFingerprintChecked() {
        var created = authority.create(create("tenant-a", "pro", "offer-pro", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-map"));
        var command = new MapProviderOfferingCommand(CatalogActor.global("admin", "catalog"), "map-1", "stripe",
                "external-pro", "external-price", created.product().productId(), created.offering().offeringId(), 1,
                0, "map-key", "catalog", "mapping", "trace-map", NOW);
        assertEquals(authority.mapProvider(command), authority.mapProvider(command));
        assertThrows(IllegalStateException.class, () -> authority.mapProvider(new MapProviderOfferingCommand(
                command.actor(), "map-2", "stripe", "external-pro", "different-price", command.productId(),
                command.offeringId(), 1, 0, "map-key", "catalog", "mapping", "trace-map", NOW)));
        assertThrows(RuntimeException.class, () -> authority.mapProvider(new MapProviderOfferingCommand(
                command.actor(), "map-3", "stripe", "external-pro", "external-price", command.productId(),
                command.offeringId(), 1, 0, "map-other-key", "catalog", "mapping", "trace-map-3", NOW)));
        assertEquals(1, count("provider_product_mapping"));
    }

    @Test void referencesAndMoneySnapshotAreExactAndNotExecutionCost() {
        var created = authority.create(create("tenant-a", "pro", "offer-pro", "GLOBAL", "US", NOW.minusSeconds(5), null, "create-money"));
        CommercialOffering offering = created.offering();
        assertEquals(new AuthorityReference("price-pro", 7), offering.commercialPriceReference());
        assertEquals(new Money(9999, "USD"), offering.priceSnapshot());
        assertEquals(new AuthorityReference("bundle-pro", 3), offering.entitlementBundleReference());
        assertEquals(new AuthorityReference("quota-pro", 4), offering.quotaProfileReference());
        assertEquals(new AuthorityReference("plan-pro", 2), offering.subscriptionPlanReference());
        assertFalse(CommercialOffering.class.getRecordComponents()[0].getType().getName().contains("ExecutionCost"));
    }

    @Test void productOfferingAndAuditRollbackTogether() {
        jdbc.execute("CREATE FUNCTION fail_catalog_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced'; END $$");
        jdbc.execute("CREATE TRIGGER fail_offering BEFORE INSERT ON commercial_offering FOR EACH ROW EXECUTE FUNCTION fail_catalog_test()");
        assertThrows(RuntimeException.class, () -> authority.create(create(
                "tenant-a", "rollback", "offer-rollback", "GLOBAL", "US", NOW, null, "rollback-key")));
        assertEquals(0, count("commerce_product"));
        assertEquals(0, count("commercial_offering"));
        assertEquals(0, count("product_catalog_command"));
    }

    private CreateCommercialOfferingCommand create(String tenant, String product, String offering,
            String tenantScope, String market, Instant from, Instant to, String key) {
        return new CreateCommercialOfferingCommand(CatalogActor.global("admin", "catalog"), "prod-" + product,
                product, ProductLineType.BASE_SUBSCRIPTION, product, offering, product + "-monthly", 1,
                PurchaseMode.SUBSCRIPTION, tenantScope, market, from, to,
                new AuthorityReference("bundle-pro", 3), new AuthorityReference("quota-pro", 4),
                new AuthorityReference("plan-pro", 2), new AuthorityReference("price-pro", 7),
                new Money(9999, "USD"), null, null, null, 0, key, "catalog", "test", "trace-" + key, NOW);
    }

    private LifecycleOfferingCommand lifecycle(String id, long expected, OfferingLifecycleState target, String key) {
        return new LifecycleOfferingCommand(CatalogActor.global("admin", "catalog"), id, expected, target,
                key, "catalog", "test", "trace-" + key, NOW);
    }

    private LifecycleProductCommand productLifecycle(String id, long expected, ProductLifecycleState target, String key) {
        return new LifecycleProductCommand(CatalogActor.global("admin", "catalog"), id, expected, target,
                key, "catalog", "test", "trace-" + key, NOW);
    }

    private CreateCommercialOfferingCommand createVersion(String product, String offering, String offeringKey,
            long offeringVersion, String market, String key) {
        return new CreateCommercialOfferingCommand(CatalogActor.global("admin", "catalog"), "prod-" + product,
                product, ProductLineType.BASE_SUBSCRIPTION, product, offering, offeringKey, offeringVersion,
                PurchaseMode.SUBSCRIPTION, "GLOBAL", market, NOW.minusSeconds(5), null,
                new AuthorityReference("bundle-pro", 3), new AuthorityReference("quota-pro", 4),
                new AuthorityReference("plan-pro", 2), new AuthorityReference("price-pro", 7),
                new Money(9999, "USD"), null, null, null, 0, key, "catalog", "test", "trace-" + key, NOW);
    }

    private static boolean run(CountDownLatch start, Runnable action) {
        try { start.await(); action.run(); return true; } catch (RuntimeException | InterruptedException e) { return false; }
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    @Configuration @EnableTransactionManagement
    static class Config {
        static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean ProductCatalogJdbcRepository repository(JdbcTemplate jdbc) { return new ProductCatalogJdbcRepository(jdbc); }
        @Bean ProductCatalogAuthority authority(ProductCatalogJdbcRepository repository) { return new ProductCatalogAuthority(repository); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
}
