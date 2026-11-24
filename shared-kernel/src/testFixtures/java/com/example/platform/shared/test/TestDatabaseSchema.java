package com.example.platform.shared.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** One class-owned schema in the approved run database; never provisions a server or role.
 * Schemas live until the complete Job ends and the Supervisor cleans its database.
 */
public final class TestDatabaseSchema implements AutoCloseable {
    private final String name;
    private final Connection connection;
    private TestDatabaseSchema(String name,Connection connection) {
        this.name=name;this.connection=connection;
    }
    public static TestDatabaseSchema open(TestDatabaseConnectionProvider provider) throws SQLException {
        provider.validateConnection();
        String schema="ts_"+UUID.randomUUID().toString().replace("-","");
        try (Connection c=provider.openConnection();var statement=c.createStatement()) {
            statement.execute("create schema \""+schema+"\"");
        }
        return new TestDatabaseSchema(schema,provider.openConnectionForSchema(schema));
    }
    public String name() { return name; }
    public Connection connection() { return connection; }
    /** Business tables retain their real domain schemas. The local schema holds only test scratch objects. */
    public org.jooq.DSLContext dsl() {
        return org.jooq.impl.DSL.using(connection,org.jooq.SQLDialect.POSTGRES);
    }

    public void migrateApplication() {
        var provider = TestDatabaseConnectionProvider.fromControlledLaunch();
        org.flywaydb.core.Flyway.configure().dataSource(provider.jdbcUrl(), provider.username(), provider.secret())
                .defaultSchema("public").locations("classpath:db/migration").validateOnMigrate(true).load().migrate();
    }
    /** Trusted baseline SQL only. PostgreSQL JDBC parses dollar-quoted/multi-statement SQL.
     * Any SQL failure fails setup; no psql/container shell, URL or credential override.
     */
    public void executeScript(String sql) throws SQLException {
        if (sql==null || sql.isBlank()) throw new IllegalArgumentException("EMPTY_MIGRATION");
        try (var statement=connection.createStatement()) { statement.execute(sql); }
    }
    @Override public void close() throws SQLException { connection.close(); }
}
