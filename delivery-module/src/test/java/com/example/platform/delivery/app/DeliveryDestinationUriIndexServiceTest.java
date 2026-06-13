package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DeliveryDestinationUriIndexServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private DeliveryDestinationUriIndexService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS delivery_destination ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64) not null,"
                + "name varchar(255) not null,"
                + "protocol varchar(32) not null,"
                + "config_json text,"
                + "enabled boolean default true,"
                + "created_at timestamp not null"
                + ")");

        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE delivery_destination CASCADE");
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
                "INSERT INTO delivery_destination (id, tenant_id, name, protocol, config_json, enabled, created_at) "
                        + "VALUES ('d1', 't1', 'cdn', 'S3', "
                        + "'{\"baseUrl\":\"https://cdn.example.com\",\"bucket\":\"tenant-a\"}', true, CURRENT_TIMESTAMP)");
        Set<String> prefixes = service.collectDestinationUriPrefixes();
        assertTrue(prefixes.contains("https://cdn.example.com/"));
        assertTrue(prefixes.contains("s3://tenant-a/"));
    }
}
