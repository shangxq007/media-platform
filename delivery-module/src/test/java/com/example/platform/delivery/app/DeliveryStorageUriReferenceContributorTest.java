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

    private static final String SCHEMA=isolatedSchemaName();
    @BeforeAll static void setUpDatabase(){
        DeliveryTestSchema.migrate(jdbcUrl(),username(),password(),SCHEMA);
        dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(
            jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+SCHEMA,username(),password());
        dsl=DSL.using(dataSource,SQLDialect.POSTGRES,new Settings().withRenderSchema(false));
    }
    @org.junit.jupiter.api.AfterAll static void close(){dsl.execute("drop schema "+SCHEMA+" cascade");closeDataSource(dataSource);}

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE delivery_job CASCADE");

        dsl.execute("INSERT INTO delivery_job (id, tenant_id, project_id, render_job_id, destination_id, status, artifact_id, remote_uri, created_at) VALUES ("
                + "'dj_1','ten','prj_1','rj_1','dest_1','COMPLETED',"
                + "'artifact-1',null,CURRENT_TIMESTAMP)");

        var index=org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactOutputReferenceIndex.class);
        org.mockito.Mockito.when(index.findByStorageUri("s3://bucket/out.mp4","prj_1",50)).thenReturn(java.util.List.of(
            new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope("ten","prj_1","rj_1"),new com.example.platform.shared.identity.ArtifactId("artifact-1"))));
        contributor = new DeliveryStorageUriReferenceContributor(dsl,index);
    }

    @Test
    void findsDeliveryJobBySourceUri() {
        assertEquals(1, contributor.findReferences("s3://bucket/out.mp4", "prj_1").size());
    }
}
