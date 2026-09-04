package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.example.platform.timeline.app.SystemMaintenanceReader;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.infrastructure.TimelineAssetGcProperties;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineAssetGcServiceTest extends PostgresTestContainerSupport {

    private static final String PROJECT = "prj_gc";
    private static final String TENANT = "ten_1";

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineAssetGcService gcService;
    private TimelineSnapshotService snapshotService;
    private String legacySnapshotId;
    private String legacyPayload;

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
        TimelineAssetLifecycleService lifecycleService =
                new TimelineAssetLifecycleService(dsl, snapshotService, List.of());
        TimelineAssetGcProperties props = new TimelineAssetGcProperties();
        props.setRetentionDays(0);
        props.setDeleteBlobOnPurge(false);
        gcService = new TimelineAssetGcService(
                mock(SystemMaintenanceReader.class), snapshotService, lifecycleService, props, null);
        RenderTestSchemaFixture.insertCanonicalProject(dsl, TENANT, PROJECT);

        legacyPayload = """
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
        legacySnapshotId = snapshotService.saveTx(
                dsl, PROJECT, TENANT, legacyPayload, "internal-1.0");
    }

    @Test
    void defersLegacyShadowAssetRegistryPurge() {
        int snapshotsBefore = snapshotCount();
        String payloadBefore = storedPayload(legacySnapshotId);

        TimelineAssetGcService.GcProjectResult result = gcService.runProjectGc(PROJECT, TENANT);

        assertEquals(PROJECT, result.projectId());
        assertEquals(0, result.candidates());
        assertEquals(0, result.purged());
        assertEquals(0, result.skipped());
        assertEquals(List.of(
                "NEEDS_ARCHITECTURE_REVIEW: legacy internal asset-registry GC is retired"),
                result.errors());
        assertEquals(snapshotsBefore, snapshotCount(), "GC must not mint a shadow snapshot");
        assertEquals(payloadBefore, storedPayload(legacySnapshotId), "legacy snapshot stays immutable");
        assertEquals(legacyPayload, payloadBefore, "the exact owned fixture must be readable");
        assertEquals(0, dsl.fetchOne(
                        "select count(*) from timeline_snapshot where schema_version = 'timeline-1.0'")
                .get(0, Integer.class), "GC must not claim a canonical TimelineDocument write");
    }

    private static int snapshotCount() {
        return dsl.fetchOne("select count(*) from timeline_snapshot").get(0, Integer.class);
    }

    private static String storedPayload(String snapshotId) {
        return dsl.fetchOne(
                        "select payload_json from timeline_snapshot where id = ?", snapshotId)
                .get(0, String.class);
    }
}
