package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryStorageUriReferenceContributorTest {

    private DeliveryStorageUriReferenceContributor contributor;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:deliveryRef;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE delivery_job ("
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
            stmt.execute("INSERT INTO delivery_job VALUES ("
                    + "'dj_1','ten','prj_1','rj_1','dest_1','COMPLETED',"
                    + "'s3://bucket/out.mp4',null,CURRENT_TIMESTAMP)");
        }
        DSLContext dsl = DSL.using(DriverManager.getConnection(jdbcUrl, "sa", ""), SQLDialect.H2);
        contributor = new DeliveryStorageUriReferenceContributor(dsl);
    }

    @Test
    void findsDeliveryJobBySourceUri() {
        assertEquals(1, contributor.findReferences("s3://bucket/out.mp4", "prj_1").size());
    }
}
