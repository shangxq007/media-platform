package com.example.platform.shared.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using PostgreSQL Testcontainers.
 *
 * <p>Provides an isolated, disposable PostgreSQL instance for each test class.
 * No hardcoded database host is allowed in CI tests.
 */
@Testcontainers
public abstract class PostgresTestContainerSupport {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("media_platform_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeoutSeconds(120);

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        // Enable Flyway for all tests
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // Disable H2-specific configurations
        registry.add("spring.sql.init.mode", () -> "never");
    }

    protected static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String username() {
        return POSTGRES.getUsername();
    }

    protected static String password() {
        return POSTGRES.getPassword();
    }

    protected static String driverClassName() {
        return POSTGRES.getDriverClassName();
    }

    /**
     * Create a DataSource using the Testcontainers PostgreSQL URL.
     * For tests that manually construct their own DataSource.
     */
    protected static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(username());
        config.setPassword(password());
        config.setDriverClassName(driverClassName());
        config.setMaximumPoolSize(3);
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
