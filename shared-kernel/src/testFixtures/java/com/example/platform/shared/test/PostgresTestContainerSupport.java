package com.example.platform.shared.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base fixture for integration tests using an external PostgreSQL 18 database.
 *
 * <p>The provider is the single connection authority. There is no container,
 * no localhost fallback, and no branch-management credential in the test JVM.
 *
 * <p>Tests that need clean-migration semantics (a pristine schema per class, so
 * Flyway state never leaks between classes) obtain a unique logical SCHEMA inside
 * this shared execution-owned runtime via {@link #isolatedSchemaName()} — NOT a new
 * physical container.
 *
 * <p>Connection-capacity contract (TEST_DATABASE_CONNECTION_CAPACITY_CONTRACT_V4):
 * the Spring-managed datasource pool is capped at {@link #SPRING_MAX_POOL_SIZE} = 6;
 * manually-built pools default to {@link #MANUAL_MAX_POOL_SIZE} = 2 (ordinary profile)
 * and may be built at {@link #TX_HEAVY_MAX_POOL_SIZE} = 3 for the transaction-heavy
 * profile via {@link #createDataSource(int)}. All pools are lazy
 * ({@code minimum-idle = 0}). MAXIMUM_POOL_SIZE IS CAPACITY, NOT PREALLOCATED
 * CONNECTION COUNT — with minIdle=0 the pool grows on demand; resident idle
 * accumulation across the cached Spring contexts is the modeled mechanism (NOT a
 * leak). Client-side reduction is the FIRST lever; the PostgreSQL server capacity
 * is NOT raised.
 */
public abstract class PostgresTestContainerSupport {

    protected static final TestDatabaseConnectionProvider PROVIDER;

    /**
     * Frozen lazy-Hikari ceiling for the Spring-managed test datasource (Contract V4).
     * Capacity, not preallocated count (minIdle=0). Do NOT reduce below the minimum
     * measured sufficient; do NOT raise server capacity to compensate.
     */
    protected static final int SPRING_MAX_POOL_SIZE = 6;
    protected static final int SPRING_MIN_IDLE = 0;
    /**
     * Frozen lazy-Hikari ceiling for the ORDINARY manually-built pool profile
     * ({@link #createDataSource()}). Part of the connection-capacity contract — do NOT increase.
     */
    protected static final int MANUAL_MAX_POOL_SIZE = 2;
    protected static final int MANUAL_MIN_IDLE = 0;
    /**
     * Frozen lazy-Hikari ceiling for the TRANSACTION-HEAVY manually-built pool profile
     * ({@link #createDataSource(int)} with {@code maxPoolSize = 3}).
     *
     * <p>Demand = 3, measured from the REQUIRES_NEW + non-Spring-bound jOOQ topology:
     * an outer transaction holds one connection, the REQUIRES_NEW inner boundary acquires a
     * second (independent) connection, and a jOOQ DSL acquired directly from the manual
     * DataSource (NOT bound to Spring's transactional ConnectionHolder) acquires a third —
     * all three may be pending concurrently. Do NOT raise; do NOT use this profile unless the
     * test's transaction topology genuinely requires three concurrent connections.
     */
    protected static final int TX_HEAVY_MAX_POOL_SIZE = 3;

    /** Frozen Spring test context cache upper bound (capacity budget term). Contract V4 = 10. */
    protected static final int CONTEXT_CACHE_MAX_SIZE = 10;

    /** Frozen lazy-Hikari ceiling for the Spring-managed test datasource (contract term). */
    protected static int springMaxPoolSize() {
        return SPRING_MAX_POOL_SIZE;
    }

    /** Frozen lazy-Hikari ceiling for the ordinary manually-built pool profile (contract term). */
    protected static int manualMaxPoolSize() {
        return MANUAL_MAX_POOL_SIZE;
    }

    /**
     * Frozen lazy-Hikari ceiling for the transaction-heavy manually-built pool profile
     * (contract term). Use only where the test's transaction topology demands three
     * concurrent connections (REQUIRES_NEW + non-Spring-bound jOOQ acquisition).
     */
    protected static int txHeavyMaxPoolSize() {
        return TX_HEAVY_MAX_POOL_SIZE;
    }

    /**
     * Frozen Spring test context cache upper bound (capacity budget term — resident bound source).
     * Expressed as an accessor so the capacity guard reads the support constant and fails on drift.
     */
    protected static int contextCacheMaxSize() {
        return CONTEXT_CACHE_MAX_SIZE;
    }

    /**
     * Generates unique per-class schema names. Atomic counter guarantees uniqueness
     * even when multiple tests initialise concurrently; the short random suffix avoids
     * collisions across JVM restarts within the same build.
     */
    private static final AtomicInteger ISOLATED_SCHEMA_COUNTER = new AtomicInteger();

    static {
        PROVIDER = TestDatabaseConnectionProvider.fromControlledLaunch();
        try {
            PROVIDER.validateConnection();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("test database preflight failed", e);
        }
    }

    /**
     * Returns a unique logical schema name for an isolation-needing test class.
     *
     * <p>The name contains only {@code [a-z0-9_]} (a valid, short PostgreSQL
     * identifier), is unique per call, and reuses the shared {@link #PROVIDER}
     * runtime — never a second container.
     */
    protected static String isolatedSchemaName() {
        int seq = ISOLATED_SCHEMA_COUNTER.getAndIncrement();
        int suffix = ThreadLocalRandom.current().nextInt(0, 0xffff);
        String run = PROVIDER.runId().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "ts_" + run + "_" + Integer.toHexString(seq) + "_" + Integer.toHexString(suffix);
    }

    /**
     * Registers Spring DataSource + Flyway properties scoped to a unique isolated
     * schema inside the shared execution-owned runtime. Use this in a
     * {@code @DynamicPropertySource} for tests that need clean-migration semantics
     * (fresh Flyway baseline per class, no cross-class state leakage).
     *
     * <p>The schema is created and owned by Flyway ({@code create-schemas=true});
     * the provider connection is shared for the run.
     */
    protected static void registerIsolatedSchemaProperties(DynamicPropertyRegistry registry, String schema) {
        registry.add("spring.datasource.url", () -> PROVIDER.scopedJdbcUrl(schema));
        registry.add("spring.datasource.username", PROVIDER::username);
        registry.add("spring.datasource.password", PROVIDER::secret);
        registry.add("spring.datasource.driver-class-name", PROVIDER::driverClassName);
        // Capacity contract: lazy pool for the Spring-managed datasource (capacity, not preallocated).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> Integer.toString(SPRING_MAX_POOL_SIZE));
        registry.add("spring.datasource.hikari.minimum-idle", () -> Integer.toString(SPRING_MIN_IDLE));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", () -> PROVIDER.scopedJdbcUrl(schema));
        registry.add("spring.flyway.user", PROVIDER::username);
        registry.add("spring.flyway.password", PROVIDER::secret);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainerSupport::registeredUrl);
        registry.add("spring.datasource.username", PROVIDER::username);
        registry.add("spring.datasource.password", PROVIDER::secret);
        registry.add("spring.datasource.driver-class-name", PROVIDER::driverClassName);
        // Capacity contract: lazy pool for the Spring-managed datasource (capacity, not preallocated).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> Integer.toString(SPRING_MAX_POOL_SIZE));
        registry.add("spring.datasource.hikari.minimum-idle", () -> Integer.toString(SPRING_MIN_IDLE));

        // Enable Flyway for all tests
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", PostgresTestContainerSupport::registeredUrl);
        registry.add("spring.flyway.user", PROVIDER::username);
        registry.add("spring.flyway.password", PROVIDER::secret);

        // Disable H2-specific configurations
        registry.add("spring.sql.init.mode", () -> "never");
    }

    protected static String jdbcUrl() {
        return PROVIDER.jdbcUrl();
    }

    protected static String username() {
        return PROVIDER.username();
    }

    protected static String password() {
        return PROVIDER.secret();
    }

    protected static String driverClassName() {
        return PROVIDER.driverClassName();
    }

    protected static java.sql.Connection openConnection() throws java.sql.SQLException {
        return PROVIDER.openConnection();
    }

    protected static java.sql.Connection openConnection(String schema) throws java.sql.SQLException {
        return PROVIDER.openConnectionForSchema(schema);
    }

    private static String registeredUrl() {
        return PROVIDER.jdbcUrl();
    }

    protected static String scopedJdbcUrl(String schema) {
        return PROVIDER.scopedJdbcUrl(schema);
    }

    protected static java.util.Properties connectionProperties() {
        return PROVIDER.connectionProperties();
    }

    /**
     * Create a lazily-initialized DataSource using the external PostgreSQL 18 URL.
     *
     * <p>ORDINARY profile: {@code maximumPoolSize = 2, minimumIdle = 0}. This is the default
     * for tests whose transaction topology never needs more than two concurrent connections.
     * For tests that genuinely require three concurrent connections (REQUIRES_NEW inner boundary
     * plus a non-Spring-bound jOOQ acquisition), use {@link #createDataSource(int)} with
     * {@link #TX_HEAVY_MAX_POOL_SIZE}.
     */
    protected static DataSource createDataSource() {
        return createDataSource(MANUAL_MAX_POOL_SIZE);
    }

    /**
     * Create a lazily-initialized DataSource with a caller-specified pool ceiling.
     *
     * <p>Use this to opt into the transaction-heavy profile ({@code maxPoolSize = 3},
     * {@link #TX_HEAVY_MAX_POOL_SIZE}) ONLY where the test's transaction topology demands three
     * concurrent connections. The ordinary profile ({@code createDataSource()}, pool = 2) is the
     * default. {@code minimumIdle} is always 0 (lazy) — capacity, never preallocated.
     *
     * @param maxPoolSize the pool ceiling; must not exceed {@link #TX_HEAVY_MAX_POOL_SIZE}
     */
    protected static DataSource createDataSource(int maxPoolSize) {
        return PROVIDER.dataSource(maxPoolSize, MANUAL_MIN_IDLE);
    }

    /** Class-owned schema with the same run, role, TLS and ordinary lazy-pool ceiling. */
    protected static DataSource createDataSourceForSchema(String schema) {
        var config = new HikariConfig();
        config.setJdbcUrl(PROVIDER.scopedJdbcUrl(schema));
        config.setUsername(PROVIDER.username());
        config.setPassword(PROVIDER.secret());
        config.setMaximumPoolSize(MANUAL_MAX_POOL_SIZE);
        config.setMinimumIdle(MANUAL_MIN_IDLE);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    /**
     * Close a DataSource if it implements AutoCloseable.
     * Should be called in @AfterAll to release connection pool resources.
     */
    protected static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Log but don't fail test cleanup
                System.err.println("Warning: Failed to close test DataSource: " + e.getMessage());
            }
        }
    }
}
