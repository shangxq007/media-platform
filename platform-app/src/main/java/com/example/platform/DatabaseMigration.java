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
        var dataSource = flyway.getConfiguration().getDataSource();
        // Reject unsafe effective privileges before V2 can change the catalog.
        try (var connection = dataSource.getConnection()) {
            apply(connection, "admission");
        }
        flyway.migrate();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            apply(connection, "admission");
            apply(connection, "runtime");
            connection.commit();
        }
    }

    private static void apply(java.sql.Connection connection, String script) throws java.io.IOException, java.sql.SQLException {
        try (var sql = DatabaseMigration.class.getResourceAsStream("/db/roles/" + script + ".sql");
             var statement = connection.createStatement()) {
            if (sql == null) throw new IllegalStateException("Missing database role script " + script);
            statement.execute(new String(sql.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
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
