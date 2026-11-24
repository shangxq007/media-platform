package com.example.platform;

/** Entry point for the existing flyway bootstrap profile, run before API and Worker startup. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Migration accepts configuration through the flyway profile");
        }
        FlywayConfiguration.migrationChain().dataSource(required("SPRING_FLYWAY_URL"),
                required("SPRING_FLYWAY_USER"), required("SPRING_FLYWAY_PASSWORD")).load().migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing migration configuration " + name);
        }
        return value;
    }
}
