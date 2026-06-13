package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DeliveryStorageUriReferenceContributorTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private DeliveryStorageUriReferenceContributor contributor;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS delivery_job ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64),"
                + "project_id varchar(64),"
                + "render_job_id varchar(64),"
                + "destination_id varchar(64),"
                + "status varchar(32),"
                + "source_uri varchar(1024),"
                + "remote_uri varchar(1024),"
                + "created_at timestamp"
                + ")");

        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE delivery_job CASCADE");

        dsl.execute("INSERT INTO delivery_job (id, tenant_id, project_id, render_job_id, destination_id, status, source_uri, remote_uri, created_at) VALUES ("
                + "'dj_1','ten','prj_1','rj_1','dest_1','COMPLETED',"
                + "'s3://bucket/out.mp4',null,CURRENT_TIMESTAMP)");

        contributor = new DeliveryStorageUriReferenceContributor(dsl);
    }

    @Test
    void findsDeliveryJobBySourceUri() {
        assertEquals(1, contributor.findReferences("s3://bucket/out.mp4", "prj_1").size());
    }
}
