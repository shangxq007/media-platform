package com.example.platform.marketplace;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class MarketplaceMigrationTest extends PostgresTestContainerSupport {
    @Test void historicalListingAndReviewEvidenceSurviveWhileInvalidPendingPreparationStops() {
        String schema=isolatedSchemaName();var admin=new JdbcTemplate(createDataSource());admin.execute("create schema "+schema);
        try {
            Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("5").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,username(),password()));
            jdbc.update("insert into tenant(id,name,status,created_at) values ('tenant','tenant','ACTIVE',now())");
            jdbc.update("insert into project(id,tenant_id,name,status,created_at) values ('historical-project','tenant','Historical','ACTIVE',now())");
            jdbc.update("insert into media_asset(id,tenant_id,project_id,storage_key,media_type,filename,media_version,publish_status,created_at) values ('asset','tenant','historical-project','old/key','VIDEO','old.mp4','v1','PUBLISHED',now())");
            jdbc.update("insert into marketplace_listing(id,asset_id,tenant_id,project_id,listing_type,title,status,version,created_at,updated_at) values ('listing','asset','tenant',null,'MEDIA','Historical title','PUBLISHED','1.0',now(),now())");
            jdbc.update("insert into timeline_review(id,project_id,tenant_id,revision_id,target_type,author_user_id,title,status,created_at,updated_at) values ('review','historical-project','tenant','asset','ASSET','historical-author','Historical review','APPROVED',now(),now())");
            String review=jdbc.queryForObject("select row_to_json(r)::text from timeline_review r where id='review'",String.class);
            for(String job:new String[]{"pending","completed"}) {
                jdbc.update("insert into platform_job(id,job_type,aggregate_type,aggregate_id,tenant_id,project_id,status,payload_json,created_at) values (?,'MARKETPLACE_PREPARE','ASSET','asset',null,null,?,'{\"assetId\":\"asset\"}',now())",job,job.equals("pending")?"PENDING":"COMPLETED");
                jdbc.update("insert into platform_task(id,job_id,task_type,capability,status,created_at) values (?,?,'PACKAGE','PACKAGE',?,now())","task-"+job,job,job.equals("pending")?"PENDING":"COMPLETED");
            }
            Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("6").load().migrate();
            assertThat(jdbc.queryForObject("select admitted_at is null from marketplace_listing where id='listing'",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select legacy_snapshot->>'title' from marketplace_listing where id='listing'",String.class)).isEqualTo("Historical title");
            assertThat(jdbc.queryForObject("select legacy_snapshot->>'status' from marketplace_listing where id='listing'",String.class)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("select row_to_json(r)::text from timeline_review r where id='review'",String.class)).isEqualTo(review);
            assertThat(jdbc.queryForObject("select status from platform_job where id='pending'",String.class)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("select payload_json from platform_job where id='pending'",String.class)).isEqualTo("{\"assetId\":\"asset\"}");
            assertThat(jdbc.queryForObject("select error_message from platform_task where id='task-pending'",String.class)).contains("MARKETPLACE_OWNER_MIGRATION");
            assertThat(jdbc.queryForObject("select status from platform_job where id='completed'",String.class)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("select count(*) from marketplace_review",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from outbox_events",Integer.class)).isZero();
        } finally {admin.execute("drop schema "+schema+" cascade");}
    }

    @Test void retiredPendingEnvelopesAreExplicitlyDeadLetteredWithoutRewritingEvidence() {
        String schema=isolatedSchemaName();var admin=new JdbcTemplate(createDataSource());admin.execute("create schema "+schema);
        try {
            Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("6").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,username(),password()));
            String payload="{\"envelopeVersion\":1,\"tenantId\":\"tenant\",\"eventType\":\"asset.published\",\"eventVersion\":1,\"aggregateType\":\"ASSET\",\"aggregateId\":\"asset\",\"payload\":{\"assetId\":\"asset\",\"assetVersion\":\"v1\",\"assetType\":\"MEDIA\",\"projectId\":\"project\",\"publishStatus\":\"PUBLISHED\"}}";
            for(String status:java.util.List.of("PENDING","FAILED","PROCESSING","PROCESSED"))
                jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,created_at,idempotency_key,locked_by,locked_at) values (?,'ASSET','asset','asset.published',1,?,?,now(),?,'old-claim',now())",status,payload,status,"key-"+status);
            Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
            for(String status:java.util.List.of("PENDING","FAILED","PROCESSING")) {
                var row=jdbc.queryForMap("select * from outbox_events where id=?",status);
                assertThat(row.get("status")).isEqualTo("DEAD_LETTER");assertThat(row.get("payload")).isEqualTo(payload);
                assertThat(row.get("event_version")).isEqualTo(1);assertThat(row.get("idempotency_key")).isEqualTo("key-"+status);
                assertThat(row.get("last_error_code")).isEqualTo("RETIRED_MARKETPLACE_EVENT_SCHEMA");assertThat(row.get("locked_by")).isNull();
            }
            assertThat(jdbc.queryForObject("select status from outbox_events where id='PROCESSED'",String.class)).isEqualTo("PROCESSED");
            assertThat(jdbc.queryForObject("select count(*) from outbox_events",Integer.class)).isEqualTo(4);
        } finally {admin.execute("drop schema "+schema+" cascade");}
    }
}
