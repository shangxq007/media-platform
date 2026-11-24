package com.example.platform.config.render;

import static com.example.platform.typedschema.jooq.generated.render.tables.RenderJob.RENDER_JOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderAcceptanceContextService;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.RenderJobStatusHistoryRepository;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 acceptance P3-B: the render job created by the Planning Application Service is really
 * persisted in {@code render_job} (real PostgreSQL, real Flyway migration, real {@link
 * RenderJobService} over the canonical jOOQ repository). No mock substitutes the persistence.
 */
class RevisionRenderPlanningApplicationServicePersistenceIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-p3b";
    private static final String PROJECT = "project-p3b";
    private static final String REVISION = "rev-p3b";
    private static final String PROFILE = "first-render";
    private static final String SNAPSHOT_ID = "snap-p3b";
    private static final String ARTIFACT_ID = "art-p3b";

    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory()).mintEmpty();

    private static DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        // render_job.project_id has an FK to project, and project.tenant_id has an FK to tenant.
        // Seed via raw SQL: the tracked jOOQ schema is a bounded identity subset (it does not
        // carry every canonical column such as project.name / created_at).
        dsl.execute("insert into identity.tenant(id, name, created_at) values (?, ?, now()) "
                + "on conflict (id) do nothing", TENANT, "P3B");
        dsl.execute("insert into identity.project(id, tenant_id, name, created_at) values (?, ?, ?, now()) "
                + "on conflict (id) do nothing", PROJECT, TENANT, "P3B");
    }

    @AfterAll
    static void closePool() {
        closeDataSource(dataSource);
    }

    @Test
    void servicePersistsTheRenderJobRowAndKeepsIdsIndependent() {
        RenderJobService renderJobService = new RenderJobService(
                new RenderJobRepository(dsl),

                mock(RenderLifecyclePublisher.class),
                new RenderJobStatusHistoryRepository(dsl),
                null,
                mock(ProjectReadQuery.class),
                mock(CanonicalActorResolver.class),
                mock(AuthorizationDecisionPort.class),
                mock(RenderAcceptanceContextService.class));

        TimelineRevisionSaveService revisionSaveService = mock(TimelineRevisionSaveService.class);
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        ArtifactQueryService artifactQueries = mock(ArtifactQueryService.class);
        ProviderInteroperabilityAuthority authority = mock(ProviderInteroperabilityAuthority.class);

        when(revisionSaveService.findById(TENANT, REVISION)).thenReturn(revision());
        String json = TimelineDocumentJsonSerializer.serialize(document());
        when(revisionQueryService.getRevisionSnapshotPayload(PROJECT, TENANT, REVISION))
                .thenReturn(Optional.of(new RevisionSnapshotPayload(SNAPSHOT_ID, json, "timeline-1.0")));
        when(artifactQueries.getArtifact(TENANT, new ArtifactId(ARTIFACT_ID))).thenReturn(Optional.of(mock(com.example.platform.artifact.domain.Artifact.class)));
        when(authority.declarations()).thenReturn(List.of());

        RevisionRenderPlanningApplicationService service = new RevisionRenderPlanningApplicationService(
                renderJobService, revisionSaveService, revisionQueryService, artifactQueries, authority);

        RevisionRenderPlanningResult result = service.prepare(new RevisionRenderPlanningRequest(
                TENANT, REVISION,
                new RenderInitiator.System("actor-sys", ActorType.SYSTEM, TENANT),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                PROFILE));

        // P3-B: the render_job row really exists, scoped to the correct tenant/project, QUEUED.
        var row = dsl.selectFrom(RENDER_JOB).where(RENDER_JOB.ID.eq(result.renderJobId())).fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.get(RENDER_JOB.PROJECT_ID)).isEqualTo(PROJECT);
        assertThat(row.get(RENDER_JOB.TENANT_ID)).isEqualTo(TENANT);
        assertThat(row.get(RENDER_JOB.STATUS)).isEqualTo("QUEUED");
        assertThat(result.renderJobId()).startsWith("rj_");

        // P3-C / P3-D re-asserted on the persisted path.
        assertThat(result.renderRequest().id().value()).isNotEqualTo(result.renderJobId());
        assertThat(result.executionPlanId().value()).isNotEqualTo(result.renderJobId());
        assertThat(result.executionPlanId().value())
                .isNotEqualTo(result.renderRequest().id().value());
    }

    private static TimelineRevision revision() {
        TimelineDocument document = document();
        String timelineDigest = new TimelineContentDigester().digest(document);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                REVISION, PROJECT, null, TimelineDocument.CURRENT_SCHEMA_VERSION, document,
                revisionSemanticDigest, Instant.EPOCH, "test-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest, PINNED_EMPTY.reference(), revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                "clip-0", "asset-0", "stream-0", ARTIFACT_ID, "a".repeat(64),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "v1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of());
    }
}
