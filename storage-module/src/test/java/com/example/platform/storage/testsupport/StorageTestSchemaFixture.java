package com.example.platform.storage.testsupport;

import org.jooq.DSLContext;

public final class StorageTestSchemaFixture {

    private StorageTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id varchar(64) primary key,
                render_job_id varchar(64) not null,
                project_id varchar(64) not null,
                storage_uri text not null,
                format varchar(32),
                resolution varchar(32),
                duration bigint,
                created_at timestamp not null
            )
        """);
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("TRUNCATE TABLE artifact RESTART IDENTITY CASCADE");
    }
}
