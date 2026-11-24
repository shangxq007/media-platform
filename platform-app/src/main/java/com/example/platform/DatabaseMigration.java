package com.example.platform;

import com.example.platform.runtime.FlywayConfiguration;

/** Entry point for the existing flyway bootstrap profile, run before API and Worker startup. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) throws java.io.IOException, java.sql.SQLException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Migration accepts configuration through the flyway profile");
        }
        var flyway = FlywayConfiguration.migrationChain().dataSource(required("SPRING_FLYWAY_URL"),
                required("SPRING_FLYWAY_USER"), required("SPRING_FLYWAY_PASSWORD")).load();
        flyway.migrate();
        try (var grants = DatabaseMigration.class.getResourceAsStream("/db/roles/runtime.sql");
             var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            if (grants == null) throw new IllegalStateException("Missing database privilege definitions");
            statement.execute(new String(grants.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing migration configuration " + name);
        }
        return value;
    }
}
