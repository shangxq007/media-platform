package com.example.platform.shared.test;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests using PostgreSQL.
 *
 * <p>Uses existing PostgreSQL container for CI stability.
 * The container is started separately and remains running.
 */
public abstract class PostgresTestContainerSupport {

    // Use existing PostgreSQL container (test-postgres on port 5433)
    protected static final String POSTGRES_URL = "jdbc:postgresql://localhost:5433/media_platform_test";
    protected static final String POSTGRES_USERNAME = "test";
    protected static final String POSTGRES_PASSWORD = "test";

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES_URL);
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Enable Flyway for all tests
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", () -> POSTGRES_URL);
        registry.add("spring.flyway.user", () -> POSTGRES_USERNAME);
        registry.add("spring.flyway.password", () -> POSTGRES_PASSWORD);

        // Disable H2-specific configurations
        registry.add("spring.sql.init.mode", () -> "never");
    }

    protected static String jdbcUrl() {
        return POSTGRES_URL;
    }

    protected static String username() {
        return POSTGRES_USERNAME;
    }

    protected static String password() {
        return POSTGRES_PASSWORD;
    }

    protected static String driverClassName() {
        return "org.postgresql.Driver";
    }

    /**
     * Create a DataSource using the PostgreSQL URL.
     * For tests that manually construct their own DataSource.
     */
    protected static PGSimpleDataSource createDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl());
        ds.setUser(username());
        ds.setPassword(password());
        return ds;
    }
}
