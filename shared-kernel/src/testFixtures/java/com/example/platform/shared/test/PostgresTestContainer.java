package com.example.platform.shared.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests using PostgreSQL.
 * 
 * <p>Uses existing PostgreSQL container for CI stability.
 */
public abstract class PostgresTestContainer {

    // Use dedicated test PostgreSQL container
    protected static final String POSTGRES_URL = "jdbc:postgresql://localhost:5433/test";
    protected static final String POSTGRES_USERNAME = "test";
    protected static final String POSTGRES_PASSWORD = "test";

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES_URL);
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // Enable Flyway for all tests
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        
        // Disable H2-specific configurations
        registry.add("spring.sql.init.mode", () -> "never");
    }
}
