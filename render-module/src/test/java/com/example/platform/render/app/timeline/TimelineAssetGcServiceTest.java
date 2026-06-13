package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.infrastructure.TimelineAssetGcProperties;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineAssetGcServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineAssetGcService gcService;
    private TimelineSnapshotService snapshotService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
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
