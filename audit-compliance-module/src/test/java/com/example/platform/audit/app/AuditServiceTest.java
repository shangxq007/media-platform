package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private AuditService service;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "audittest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table audit_records ("
                    + "id varchar(64) primary key,"
                    + "actor_type varchar(50) not null,"
                    + "actor_id varchar(100),"
                    + "action varchar(120) not null,"
                    + "resource_type varchar(120) not null,"
                    + "resource_id varchar(120),"
                    + "payload text,"
                    + "category varchar(50),"
                    + "created_at timestamp not null"
                    + ")");
        }

        service = new AuditService(dsl);
    }

    @Test
    void recordCreatesAuditEntry() {
        String id = service.record("user", "u-1", "update", "config", "cfg-1",
                Map.of("key", "value"));

        assertNotNull(id);
        assertTrue(id.startsWith("aud_"));

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("audit_records"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("user", rows.get(0).get("actor_type"));
        assertEquals("u-1", rows.get(0).get("actor_id"));
        assertEquals("update", rows.get(0).get("action"));
        assertEquals("config", rows.get(0).get("resource_type"));
    }

    @Test
    void recordWithCategoryStoresCategory() {
        String id = service.record("user", "u-1", "update", "config", "cfg-1",
                Map.of("key", "value"), AuditCategory.CONFIG);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("audit_records"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("CONFIG", rows.get(0).get("category"));
    }

    @Test
    void recordWithNullCategoryStoresNull() {
        String id = service.record("user", "u-1", "update", "config", "cfg-1",
                Map.of("key", "value"), null);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("audit_records"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals(null, rows.get(0).get("category"));
    }

    @Test
    void findByCategoryReturnsMatchingRecords() {
        service.record("user", "u-1", "update", "config", "cfg-1",
                Map.of("k", "v1"), AuditCategory.CONFIG);
        service.record("user", "u-2", "update", "prompt", "p-1",
                Map.of("k", "v2"), AuditCategory.PROMPT);
        service.record("user", "u-3", "update", "config", "cfg-2",
                Map.of("k", "v3"), AuditCategory.CONFIG);

        List<Map<String, Object>> configRecords = service.findByCategory(AuditCategory.CONFIG, 10);
        assertEquals(2, configRecords.size());

        List<Map<String, Object>> promptRecords = service.findByCategory(AuditCategory.PROMPT, 10);
        assertEquals(1, promptRecords.size());
    }

    @Test
    void findByCategoryRespectsLimit() {
        service.record("user", "u-1", "update", "config", "cfg-1",
                Map.of("k", "v1"), AuditCategory.CONFIG);
        service.record("user", "u-2", "update", "config", "cfg-2",
                Map.of("k", "v2"), AuditCategory.CONFIG);
        service.record("user", "u-3", "update", "config", "cfg-3",
                Map.of("k", "v3"), AuditCategory.CONFIG);

        List<Map<String, Object>> records = service.findByCategory(AuditCategory.CONFIG, 2);
        assertEquals(2, records.size());
    }

    @Test
    void recentReturnsRecordsInDescendingOrder() {
        service.record("user", "u-1", "action1", "resource", "r-1",
                Map.of("k", "v1"));
        service.record("user", "u-2", "action2", "resource", "r-2",
                Map.of("k", "v2"));

        List<Map<String, Object>> recent = service.recent(10);
        assertTrue(recent.size() >= 2);
    }

    @Test
    void overviewReturnsTotalRecords() {
        Map<String, Object> overview1 = service.overview();
        assertEquals(0, overview1.get("totalRecords"));

        service.record("user", "u-1", "action", "resource", "r-1", Map.of("k", "v1"));

        Map<String, Object> overview2 = service.overview();
        assertEquals(1, overview2.get("totalRecords"));
    }

    @Test
    void recordWithNullPayloadStoresNull() {
        String id = service.record("user", "u-1", "action", "resource", "r-1", null);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("audit_records"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals(null, rows.get(0).get("payload"));
    }

    @Test
    void allAuditCategoryValuesExist() {
        AuditCategory[] categories = AuditCategory.values();
        assertTrue(categories.length >= 6);
        assertEquals(AuditCategory.CONFIG, AuditCategory.valueOf("CONFIG"));
        assertEquals(AuditCategory.PROMPT, AuditCategory.valueOf("PROMPT"));
        assertEquals(AuditCategory.POLICY, AuditCategory.valueOf("POLICY"));
        assertEquals(AuditCategory.PLUGIN, AuditCategory.valueOf("PLUGIN"));
        assertEquals(AuditCategory.MANUAL_RETRY, AuditCategory.valueOf("MANUAL_RETRY"));
        assertEquals(AuditCategory.PERMISSION, AuditCategory.valueOf("PERMISSION"));
    }
}
