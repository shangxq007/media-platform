package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.PostgresNotificationService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.FixturePath;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.api.event.TimelineOutboxEvents;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REVISION-CREATED-EVENT: the canonical revision creation path
 * ({@code TimelineRevisionSaveService.saveRevisionInternal}) must append a
 * {@code REVISION_CREATED} outbox event in the SAME physical transaction as the
 * revision/ref/HEAD write.
 *
 * <p>Real PostgreSQL (Testcontainers) with the single canonical {@code V1}
 * baseline applied from the repository, and a real
 * {@link OutboxEventService} + {@link OutboxEventRouter}; the assertions read the
 * actual {@code outbox_events} rows.
 */
class RevisionCreatedEventTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-rce";
    private static final String EVENT_TYPE = "timeline.revision.created";

    private static DataSource dataSource;
    private static DSLContext dsl;
    private static OutboxEventService outbox;

    private TimelineRevisionSaveService service;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        dataSource = createDataSource();
        applyMigration("V1__baseline.sql");
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        outbox = new OutboxEventService(dsl, 3,
                new PostgresNotificationService(new JdbcTemplate(dataSource)),
                new OutboxEventRouter(List.of(new TimelineOutboxEvents())));
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("truncate table infrastructure.outbox_events, project, product cascade");
        TenantContext.set(TENANT);
        service = new TimelineRevisionSaveService(
                dsl,
                new TimelineRevisionRefMutation(dsl),
                new TimelineContentDigester(),
                new TimelineSnapshotService(dsl),
                org.mockito.Mockito.mock(TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(new TimelineRevisionRefMutation(dsl)),
                TestTimelineMutationContexts.ALLOW_ALL,
                outbox);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void revisionCreatedEventAppendedOnCommit() {
        String productId = seedProduct();

        TimelineRevision revision = save(productId, null, sampleDocument());

        assertEquals(1, revisionCreatedCount(revision.revisionId()),
                "exactly one REVISION_CREATED row for the committed revision");
    }

    @Test
    void revisionCreatedEventNotAppendedOnRollback() {
        String productId = seedProduct();
        TimelineRevision first = save(productId, null, sampleDocument());
        assertEquals(1, revisionCreatedCount(first.revisionId()));

        // Second save with a stale expected head (null again) fails inside the
        // canonical transaction (HEAD CAS) and rolls the whole write back.
        assertThrows(TimelineConflictException.class,
                () -> save(productId, null, sampleDocument()));

        assertEquals(0, totalRevisionCreatedCount() - 1,
                "the rolled-back attempt must not leave an additional REVISION_CREATED row");
    }

    @Test
    void revisionCreatedEventHasCorrectPayload() {
        String productId = seedProduct();

        TimelineRevision revision = save(productId, null, sampleDocument());

        Map<String, Object> row = dsl.fetchOne(
                "select aggregate_type, aggregate_id, event_type, event_version, payload "
                        + "from infrastructure.outbox_events where event_type = ? and aggregate_id = ?",
                EVENT_TYPE, revision.revisionId()).intoMap();
        assertNotNull(row, "REVISION_CREATED row present");
        assertEquals("timeline_revision", row.get("aggregate_type"));
        assertEquals(EVENT_TYPE, row.get("event_type"));

        Object decoded = new OutboxEventRouter(List.of(new TimelineOutboxEvents())).decode(
                EVENT_TYPE, 2, "timeline_revision", revision.revisionId(),
                (String) row.get("payload"));
        assertNotNull(decoded);
        String payload = (String) row.get("payload");
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains(revision.revisionId()),
                "payload must carry the new revisionId: " + payload);
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains(TENANT),
                "payload must carry the tenant scope");
    }

    // ---- helpers ----

    private static void applyMigration(String fileName) throws Exception {
        Path dir = FixturePath.platformRoot()
                .resolve("platform-app/src/main/resources/db/migration");
        String script = Files.readString(dir.resolve(fileName));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    private String seedProduct() {
        String productId = "prod-rce-" + java.util.UUID.randomUUID();
        dsl.execute("insert into identity.tenant (id, name, status, created_at) values (?, ?, 'ACTIVE', now()) "
                        + "on conflict (id) do nothing",
                TENANT, "rce-tenant");
        dsl.execute("insert into identity.project (id, tenant_id, name, description, status, created_at) "
                        + "values (?, ?, ?, ?, ?, now())",
                productId, TENANT, "rce-project", "", "ACTIVE");
        dsl.execute("insert into render.product (product_id, product_type, representation_kind, status, "
                        + "tenant_id, project_id, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, now(), now())",
                productId, "video", "master", "REGISTERED", TENANT, productId);
        return productId;
    }

    private TimelineRevision save(String productId, String expectedHead, TimelineDocument document) {
        CanonicalActor actor = CanonicalActor.user("actor-rce", TENANT, Set.of(), "test");
        return service.saveRevision(
                new TimelineMutationContext(TENANT, productId, actor), expectedHead, document);
    }

    private long revisionCreatedCount(String revisionId) {
        Integer count = dsl.fetchOne(
                "select count(*) from infrastructure.outbox_events where event_type = ? and aggregate_id = ?",
                EVENT_TYPE, revisionId).get(0, Integer.class);
        return count == null ? 0 : count;
    }

    private long totalRevisionCreatedCount() {
        Integer count = dsl.fetchOne(
                "select count(*) from infrastructure.outbox_events where event_type = ?", EVENT_TYPE)
                .get(0, Integer.class);
        return count == null ? 0 : count;
    }

    private static TimelineDocument sampleDocument() {
        TimelineClip clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }
}
