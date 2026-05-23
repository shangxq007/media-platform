package com.example.platform.production;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Readiness: database reachable and Flyway applied when enabled.
 */
@Component("platformReadiness")
@ConditionalOnBean(DataSource.class)
public class PlatformReadinessHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final ObjectProvider<Flyway> flyway;
    private final Environment environment;

    public PlatformReadinessHealthIndicator(
            DataSource dataSource, ObjectProvider<Flyway> flyway, Environment environment) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.environment = environment;
    }

    @Override
    public Health health() {
        try (var conn = dataSource.getConnection()) {
            if (!conn.isValid(3)) {
                return Health.down().withDetail("database", "connection invalid").build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("database", e.getMessage()).build();
        }

        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            return Health.up().withDetail("flyway", "disabled").build();
        }

        Flyway fw = flyway.getIfAvailable();
        if (fw == null) {
            return Health.down().withDetail("flyway", "enabled but bean missing").build();
        }
        try {
            var info = fw.info();
            int pending = info.pending().length;
            if (pending > 0) {
                return Health.down()
                        .withDetail("flyway", "pending migrations")
                        .withDetail("pendingCount", pending)
                        .build();
            }
            return Health.up()
                    .withDetail("flyway", "applied")
                    .withDetail("latest", info.current() != null ? info.current().getVersion().getVersion() : "none")
                    .build();
        } catch (FlywayException e) {
            return Health.down().withDetail("flyway", e.getMessage()).build();
        }
    }
}
