package com.example.platform.outbox.testsupport;

import org.jooq.DSLContext;

public final class OutboxEventTestSchemaFixture {

    private OutboxEventTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS outbox_events (
                id varchar(64) primary key,
                aggregate_type varchar(100) not null,
                aggregate_id varchar(100) not null,
                event_type varchar(150) not null,
                event_version int not null,
                payload text not null,
                status varchar(50) not null,
                retry_count int not null default 0,
                max_retries int not null default 3,
                next_attempt_at timestamp with time zone,
                idempotency_key varchar(255),
                last_error_code varchar(100),
                last_error_message text,
                locked_at timestamp with time zone,
                locked_by varchar(255),
                created_at timestamp with time zone not null,
                published_at timestamp with time zone
            )
        """);
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("TRUNCATE TABLE outbox_events RESTART IDENTITY CASCADE");
    }
}
