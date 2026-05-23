package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.infrastructure.TimelineAssetGcProperties;
import com.example.platform.shared.web.ErrorCodeRegistry;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineAssetGcServiceTest {

    private TimelineAssetGcService gcService;
    private TimelineSnapshotService snapshotService;
    private DSLContext dsl;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:timelineGc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE timeline_snapshot ("
                    + "id varchar(64) primary key,"
                    + "project_id varchar(64) not null,"
                    + "tenant_id varchar(64),"
                    + "payload_json clob not null,"
                    + "schema_version varchar(32),"
                    + "created_at timestamp not null"
                    + ")");
        }
        dsl = DSL.using(DriverManager.getConnection(jdbcUrl, "sa", ""), SQLDialect.H2);
        snapshotService = new TimelineSnapshotService(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        TimelineAssetLifecycleService lifecycleService =
                new TimelineAssetLifecycleService(dsl, snapshotService, registry, List.of());
        TimelineAssetGcProperties props = new TimelineAssetGcProperties();
        props.setRetentionDays(0);
        props.setDeleteBlobOnPurge(false);
        gcService = new TimelineAssetGcService(dsl, snapshotService, lifecycleService, props, null);

        String json = """
                {
                  "schemaVersion": "1.0",
                  "revision": 1,
                  "assetRegistry": {
                    "assets": {
                      "asset_old": {
                        "id": "asset_old",
                        "uri": "s3://bucket/old.mp4",
                        "status": "TOMBSTONED",
                        "tombstonedAt": "2020-01-01T00:00:00Z"
                      }
                    }
                  },
                  "composition": { "tracks": [] }
                }
                """;
        snapshotService.save("prj_gc", "ten_1", json, "1.0");
    }

    @Test
    void purgesDeletableTombstonedAssets() {
        TimelineAssetGcService.GcProjectResult result = gcService.runProjectGc("prj_gc", "ten_1");
        assertEquals(1, result.purged());
    }
}
