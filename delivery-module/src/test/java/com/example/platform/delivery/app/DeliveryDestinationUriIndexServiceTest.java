package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryDestinationUriIndexServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private DeliveryDestinationUriIndexService service;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "desturi" + COUNTER.incrementAndGet();
        Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table delivery_destination ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "name varchar(255) not null,"
                    + "protocol varchar(32) not null,"
                    + "config_json clob,"
                    + "enabled boolean default true,"
                    + "created_at timestamp not null"
                    + ")");
        }
        service = new DeliveryDestinationUriIndexService(dsl);
    }

    @Test
    void normalizePrefixAddsTrailingSlash() {
        assertEquals("s3://bucket/", DeliveryDestinationUriIndexService.normalizePrefix("s3://bucket"));
        assertEquals("https://cdn.example.com/", DeliveryDestinationUriIndexService.normalizePrefix("https://cdn.example.com/"));
    }

    @Test
    void collectDestinationUriPrefixesFromConfig() {
        dsl.execute(
                "insert into delivery_destination (id, tenant_id, name, protocol, config_json, enabled, created_at) "
                        + "values ('d1', 't1', 'cdn', 'S3', "
                        + "'{\"baseUrl\":\"https://cdn.example.com\",\"bucket\":\"tenant-a\"}', true, current_timestamp)");
        Set<String> prefixes = service.collectDestinationUriPrefixes();
        assertTrue(prefixes.contains("https://cdn.example.com/"));
        assertTrue(prefixes.contains("s3://tenant-a/"));
    }
}
