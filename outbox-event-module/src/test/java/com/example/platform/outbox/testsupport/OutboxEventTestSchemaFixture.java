package com.example.platform.outbox.testsupport;

import org.jooq.DSLContext;

public final class OutboxEventTestSchemaFixture {

    private OutboxEventTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        dsl.connection(connection -> org.flywaydb.core.Flyway.configure()
                .dataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true))
                .locations("filesystem:../platform-app/src/main/resources/db/migration")
                .load().migrate());
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("TRUNCATE TABLE outbox_events RESTART IDENTITY CASCADE");
    }
}
