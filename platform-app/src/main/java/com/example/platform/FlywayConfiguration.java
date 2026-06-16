package com.example.platform;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Manual Flyway configuration for PostgreSQL preview startup.
 *
 * This is required because Spring Boot 4.0.4's FlywayAutoConfiguration
 * does not properly initialize in the current setup. This bean ensures
 * migrations run after DataSource is created but before application starts.
 *
 * TODO: Remove this when Spring Boot Flyway auto-configuration works correctly.
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = false)
public class FlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfiguration.class);

    @Bean
    public Flyway flyway(DataSource dataSource) {
        log.info("Running Flyway migration...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        log.info("Flyway migration completed");
        return flyway;
    }
}
