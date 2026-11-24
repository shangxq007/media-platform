package com.example.platform.runtime;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Both processes validate the migration history through their ordinary read privileges. */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class FlywayConfiguration {
    public static FluentConfiguration migrationChain() {
        return Flyway.configure().defaultSchema("public").locations("classpath:db/migration")
                .ignoreMigrationPatterns(new String[0]).baselineOnMigrate(false).validateMigrationNaming(true).validateOnMigrate(true);
    }

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = migrationChain().dataSource(dataSource).load();
        flyway.validate();
        if (flyway.info().current() == null || flyway.info().pending().length != 0) {
            throw new IllegalStateException("Database migration required before runtime startup");
        }
        return flyway;
    }
}
