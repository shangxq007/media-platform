package com.example.platform.prompt.testsupport;

import org.jooq.DSLContext;

public final class PromptTestSchemaFixture {

    private PromptTestSchemaFixture() {}

    public static void createSchema(DSLContext dsl) {
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS prompt_template (
                template_id varchar(64) primary key,
                name varchar(255) not null,
                description text,
                category varchar(128),
                tags text,
                owner varchar(128),
                status varchar(32) not null default 'DRAFT',
                schema_version varchar(32) not null default '1.0.0',
                current_prompt_version varchar(32),
                created_at timestamp not null,
                updated_at timestamp not null
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS prompt_template_version (
                version_id varchar(64) primary key,
                template_id varchar(64) not null,
                prompt_version varchar(32) not null,
                template_body text not null,
                variable_schema_json text,
                changelog text,
                created_by varchar(128),
                created_at timestamp not null,
                checksum varchar(64),
                previous_version varchar(32),
                deprecated boolean not null default false
            )
        """);

        dsl.execute("""
            CREATE TABLE IF NOT EXISTS prompt_execution_run (
                execution_id varchar(64) primary key,
                template_id varchar(64) not null,
                prompt_version varchar(32) not null,
                tenant_id varchar(64) not null,
                user_id varchar(128) not null,
                model_provider varchar(64),
                model_name varchar(64),
                rendered_prompt_hash varchar(64),
                redacted_prompt_preview varchar(512),
                input_variables_redacted_json text,
                output_summary text,
                status varchar(32) not null,
                risk_level varchar(32) not null,
                token_estimate int not null,
                cost_estimate double precision not null,
                started_at timestamp not null,
                finished_at timestamp,
                error_code varchar(64),
                error_details_json text,
                related_prompt_file varchar(256),
                related_manifest_entry varchar(256)
            )
        """);
    }

    public static void truncate(DSLContext dsl) {
        dsl.execute("""
            TRUNCATE TABLE
                prompt_execution_run,
                prompt_template_version,
                prompt_template
            RESTART IDENTITY CASCADE
        """);
    }
}
