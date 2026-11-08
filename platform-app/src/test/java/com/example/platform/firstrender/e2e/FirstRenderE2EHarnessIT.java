package com.example.platform.firstrender.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityConfiguration;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionPlanningEntry;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.persistence.binding.JooqBoundGraphInputStore;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderCapabilityVocabulary;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.runtime.PlatformFfmpegWorkerApplication;
import com.example.platform.runtime.mediatask.AdmissionEvidenceProducer;
import com.example.platform.runtime.mediatask.FirstRenderGrantMinter;
import com.example.platform.providerplugin.PendingNativeWorkProjection;
import com.example.platform.workerfabric.domain.AvailabilityState;
import com.example.platform.workerfabric.domain.CapacitySnapshot;
import com.example.platform.workerfabric.domain.CpuCapacity;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionBackendEligibilityDecision;
import com.example.platform.workerfabric.domain.ExecutionBackendEligibilityEvaluator;
import com.example.platform.workerfabric.domain.ExecutionBackendSelection;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.HostResourceSnapshotFreshnessPolicy;
import com.example.platform.workerfabric.domain.HostResourceSnapshotSchemaVersion;
import com.example.platform.workerfabric.domain.LocalWorkerRuntimeIncarnationBinding;
import com.example.platform.workerfabric.domain.MemoryCapacity;
import com.example.platform.workerfabric.domain.NativeRuntimeEligibilityRequest;
import com.example.platform.workerfabric.domain.ObservedCpuUsage;
import com.example.platform.workerfabric.domain.ObservedDeviceUsage;
import com.example.platform.workerfabric.domain.ObservedMemoryUsage;
import com.example.platform.workerfabric.domain.ObservedTemporaryStorageUsage;
import com.example.platform.workerfabric.domain.ObservedUsage;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.PhysicalHostAvailability;
import com.example.platform.workerfabric.domain.PhysicalHostDescriptor;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkValidationContext;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.RuntimeEligibilityDecision;
import com.example.platform.workerfabric.domain.RuntimeEligibilityEvaluator;
import com.example.platform.workerfabric.domain.SafetyHeadroom;
import com.example.platform.workerfabric.domain.SchedulableCapacity;
import com.example.platform.workerfabric.domain.TemporaryStorageCapacity;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.domain.WorkerReadinessEvidence;
import com.example.platform.workerfabric.domain.WorkerRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerRuntimeDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.read.IntegrityRequirement;
import com.example.platform.storage.contract.read.StorageReadRequest;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.workerfabric.domain.LocalHostRegistrationLoop;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.ReservationFeasibility;
import com.example.platform.workerfabric.domain.ReservationFeasibilityQuery;
import com.example.platform.workerfabric.domain.TaskResourceDemandDeriver;
import com.example.platform.workerfabric.infrastructure.JooqReservationFeasibilityQuery;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflow;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P1-1A.5 E2E R2 — Conduit B single-JVM harness.
 *
 * <p>Boots the real ffmpeg WORKER application (real catalog, real runtime bindings, real media task
 * activity, real grant minter/admission, real in-process Temporal test server) and additionally
 * component-scans the real Timeline module so a real, durably-persisted Timeline Revision can be
 * written and re-read (hydrated). Planning/physical-planning/binding use the real production entry
 * services; bound-graph inputs are persisted through the real {@link JooqBoundGraphInputStore}.
 *
 * <p>Only ports orthogonal to the verified chain (authorization actor/decision) are stubbed; nothing
 * on the planning/binding/grant/admission/workflow/worker/ffmpeg path is mocked.
 */
@SpringBootTest(
        classes = {
            PlatformFfmpegWorkerApplication.class,
            FirstRenderE2EHarnessIT.HarnessConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.temporal.start-workers=true",
            "spring.temporal.test-server.enabled=true",
            "app.temporal.worker-required=true",
            "spring.main.web-application-type=none"
        })
@ActiveProfiles({"test", "temporal", "ffmpeg-worker"})
class FirstRenderE2EHarnessIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-fr-e2e";
    private static final String PROJECT = "project-fr-e2e";

    /** Host-visible runtime roots. NOT under /tmp: the sandbox bind-mounts a private tmpfs on /tmp. */
    private static final Path WORK = Path.of(
            System.getProperty("user.dir"), "build", "first-render-e2e-runtime").toAbsolutePath().normalize();

    private static final Path FFMPEG = Path.of("/tmp/ffmpeg-x264/bin/ffmpeg");
    private static final Path FFPROBE = Path.of("/tmp/ffmpeg-x264/bin/ffprobe");

    @DynamicPropertySource
    static void harnessProperties(DynamicPropertyRegistry registry) throws Exception {
        // Apply the canonical migrations to the isolated Testcontainers database explicitly
        // (the worker context does not auto-run Flyway in this slice).
        org.flywaydb.core.Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        Path plugins = Files.createDirectories(WORK.resolve("plugins"));
        Path workspace = Files.createDirectories(WORK.resolve("workspace"));
        Files.createDirectories(WORK.resolve("staging"));
        Files.createDirectories(WORK.resolve("work"));
        Files.createDirectories(WORK.resolve("object-store"));
        Files.createDirectories(WORK.resolve("local-storage"));

        // Provide the real provider plugin JAR to the PF4J host.
        Path pluginJar = locatePluginJar();
        Files.copy(pluginJar, plugins.resolve(pluginJar.getFileName().toString()),
                StandardCopyOption.REPLACE_EXISTING);

        registry.add("platform.ffmpeg-worker.plugins-directory", () -> plugins.toString());
        registry.add("platform.ffmpeg-worker.workspace-root", () -> workspace.toString());
        registry.add("platform.ffmpeg-worker.staging-root", () -> WORK.resolve("staging").toString());
        registry.add("platform.ffmpeg-worker.work-root", () -> WORK.resolve("work").toString());
        registry.add("platform.ffmpeg-worker.sandbox.ffmpeg", () -> FFMPEG.toString());
        registry.add("platform.ffmpeg-worker.sandbox.ffprobe", () -> FFPROBE.toString());
        registry.add("platform.ffmpeg-worker.sandbox.bwrap", () -> "/usr/bin/bwrap");
        registry.add("platform.ffmpeg-worker.publication.project-id", () -> PROJECT);
        registry.add("platform.ffmpeg-worker.publication.storage-provider", () -> "local-object-store");
        registry.add("app.cover-image.storage.root", () -> WORK.resolve("object-store").toString());
        registry.add("app.storage.local-root", () -> WORK.resolve("local-storage").toString());
        registry.add("app.cover-image.work-root", () -> WORK.resolve("cover-work").toString());
        registry.add("platform.ffmpeg-worker.materialization-root", () -> WORK.resolve("materialized").toString());
    }

    @TestConfiguration
    @ComponentScan(
            basePackages = "com.example.platform.timeline",
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Controller"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Resource")
            })
    @Import(ProviderInteroperabilityConfiguration.class)
    static class HarnessConfig {

        @Bean
        BoundGraphInputStore boundGraphInputStore(DSLContext dsl) {
            return new JooqBoundGraphInputStore(dsl);
        }

        /** Orthogonal to the verified chain: a permissive identity decision for the revision writer. */
        @Bean
        AuthorizationDecisionPort authorizationDecisionPort() {
            AuthorizationDecisionPort port = mock(AuthorizationDecisionPort.class);
            when(port.decide(any())).thenReturn(AuthorizationDecision.allow("e2e-harness"));
            return port;
        }

        @Bean
        CanonicalActorResolver canonicalActorResolver() {
            return mock(CanonicalActorResolver.class);
        }

        /** Jackson mapper: the worker (web=none) context does not auto-configure one. */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        /**
         * Diagnostic only: decorates the real read model so the minter's ACTUAL reservation-feasibility
         * inputs and result are observable. It does not change the value or bypass anything.
         */
        @Bean
        @Primary
        ReservationFeasibilityQuery loggingReservationFeasibilityQuery(
                JooqReservationFeasibilityQuery delegate) {
            return (host, incarnation, generation, demand) -> {
                ReservationFeasibility result = delegate.assess(host, incarnation, generation, demand);
                System.out.println("[FR-E2E-DIAG] assess host=" + host.value()
                        + " inc=" + incarnation.value() + " gen=" + generation.value()
                        + " demand=" + demand + " -> " + result);
                return result;
            };
        }
    }

    @Autowired TimelineRevisionSaveService revisionSaveService;
    @Autowired TimelineRevisionQueryService revisionQueryService;
    @Autowired EffectSemanticSnapshotStore effectSnapshotStore;
    @Autowired ProviderInteroperabilityAuthority interoperabilityAuthority;
    @Autowired ProviderPluginCatalog providerPluginCatalog;
    @Autowired BoundGraphInputStore boundGraphInputStore;
    @Autowired LocalHostRegistrationLoop registrationLoop;
    @Autowired WorkflowClient workflowClient;
    @Autowired ArtifactCommitService artifactCommitService;
    @Autowired ArtifactQueryService artifactQueryService;
    @Autowired StorageProvider storageProvider;
    @Autowired List<StorageProvider> storageProviders;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationFeasibilityQuery reservationFeasibilityQuery;
    @Autowired WorkerFabricRegistrationBoundary registrationBoundary;
    @Autowired AdmissionEvidenceProducer admissionEvidenceProducer;
    @Autowired Clock clock;

    @Test
    void firstRenderRunsEndToEndAndProducesARealH264Artifact() throws Exception {
        // ---- seed tenant/project (FK prerequisites) ----
        jdbc.execute("insert into tenant(id, name, created_at) values ('" + TENANT + "', 'FR-E2E', now()) on conflict (id) do nothing");
        jdbc.execute("insert into project(id, tenant_id, name, created_at) values ('" + PROJECT + "', '" + TENANT + "', 'FR-E2E', now()) on conflict (id) do nothing");
        stage("seed tenant/project");

        // ---- 1. seed a real source Artifact (bytes = repo fixture) ----
        byte[] sourceBytes = Files.readAllBytes(
                Path.of(getClass().getResource("/render-output-fixture.mp4").toURI()));
        String sourceDigestHex = sha256Hex(sourceBytes);
        ContentDigest sourceDigest = ContentDigest.sha256(sourceDigestHex);
        String sourceArtifactId = "art-" + UUID.randomUUID();
        var session = storageProvider.beginWrite("e2e-source", sourceNamespace(), sourceDigest, sourceBytes.length);
        storageProvider.write(session, sourceBytes, 0, sourceBytes.length);
        var written = storageProvider.completeWrite(session, sourceDigest);
        artifactCommitService.commit(new ArtifactCommitRequest(
                new ArtifactId(sourceArtifactId), TENANT, sourceDigest, sourceBytes.length,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, Artifact.CURRENT_SCHEMA_VERSION,
                written.objectId(), written.replicaId(), storageProvider.providerId(),
                ReplicaRole.PRIMARY, "local", "e2e-source-" + sourceArtifactId, List.of(),
                Instant.now(), Instant.now(), null, PROJECT));
        stage("seed source artifact");

        // ---- 2. write a real persisted Timeline Revision (real canonical writer) ----
        TimelineDocument document = document(sourceArtifactId, sourceDigestHex);
        CanonicalActor actor = CanonicalActor.user("actor-fr-e2e", TENANT, java.util.Set.of("owner"), "test");
        TimelineRevision saved = revisionSaveService.saveRevision(
                new TimelineMutationContext(TENANT, PROJECT, actor), null, document);
        String revisionId = saved.revisionId();
        stage("persist revision " + revisionId);

        // ---- 3. real read + hydrate ----
        TimelineRevision revision = revisionSaveService.findById(TENANT, revisionId);
        assertThat(revision).isNotNull();
        RevisionSnapshotPayload payload = revisionQueryService
                .getRevisionSnapshotPayload(PROJECT, TENANT, revisionId).orElseThrow();
        TimelineRevision hydrated = revision.hydrate(
                TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson()));
        EffectSemanticSnapshot pinnedSnapshot = effectSnapshotStore.findById(
                PROJECT, TENANT, hydrated.effectSemanticSnapshotReference().snapshotId()).orElseThrow();
        stage("read + hydrate revision");

        // ---- 4. real #20 render planning ----
        RenderRequest renderRequest = new RenderRequest(
                new RenderRequestId(UUID.randomUUID().toString()),
                new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(1000), FrameRate.of(1, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
        boolean sourceResolved = artifactQueryService
                .getArtifact(TENANT, new ArtifactId(sourceArtifactId)).isPresent();
        SourceResolutionInput sourceResolution = new SourceResolutionInput(java.util.Map.of(
                new ArtifactId(sourceArtifactId),
                sourceResolved ? RenderSourceResolutionState.RESOLVED : RenderSourceResolutionState.FAILED));
        CapabilityContext capabilityContext = new CapabilityContext(java.util.Set.of(
                RenderCapabilityVocabulary.videoDecode().capabilityId(),
                RenderCapabilityVocabulary.outputEncode().capabilityId()));
        RenderPlanningResult renderPlanningResult = new RenderPlanningEntryService().plan(
                hydrated, pinnedSnapshot, renderRequest, sourceResolution, capabilityContext);
        assertThat(renderPlanningResult.status())
                .as("render planning diagnostics: %s", renderPlanningResult.diagnostics())
                .isEqualTo(RenderPlanStatus.PLANNABLE);
        stage("#20 render planning PLANNABLE");

        // ---- 5. real #21 physical planning ----
        ExecutionPlanId executionPlanId = new ExecutionPlanId(UUID.randomUUID().toString());
        ExecutionPlanningEntry.PlanningResult planningResult =
                new PhysicalPlanningEntryService().plan(renderPlanningResult, executionPlanId);
        PhysicalExecutionPlan physicalPlan = planningResult.physicalExecutionPlan();
        stage("#21 physical plan units=" + physicalPlan.units().size());

        // ---- 6. real provider candidates + #22 binding ----
        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(providerPluginCatalog);
        stage("catalog contributions=" + providerPluginCatalog.contributions().size()
                + " candidates=" + candidates.stream()
                        .map(c -> c.bindingPin().providerImplementationId().value()
                                + "/lowering=" + c.staticCompatibility().loweringSupport())
                        .toList());
        assertThat(candidates).as("provider catalog candidates").isNotEmpty();
        List<ProviderBoundaryCompatibilityDeclaration> declarations = interoperabilityAuthority.declarations();
        ProviderBindingEntryService.ProviderBindingOutcome binding =
                new ProviderBindingEntryService().bind(physicalPlan, candidates, declarations);
        var graph = binding.executableTaskGraph();
        assertThat(graph.tasks()).isNotEmpty();
        stage("#22 bound graph tasks=" + graph.tasks().size());

        // ---- 7. persist bound graph inputs (real JooqBoundGraphInputStore) ----
        String renderJobId = "rj_" + UUID.randomUUID().toString().replace("-", "");
        stage("units=" + physicalPlan.units().stream()
                .map(u -> u.operationKey() + "@temporalWindow=" + (u.temporalWindow() != null))
                .toList());
        BoundGraphInputs inputs = new BoundGraphInputs(
                physicalPlan, candidates, declarations, graph.digest());
        BoundGraphReference reference = boundGraphInputStore.save(inputs, TENANT, renderJobId);
        BoundGraphInputs reloaded = boundGraphInputStore.load(reference);
        assertThat(reloaded.expectedExecutableTaskGraphDigest().sha256Hex())
                .isEqualTo(graph.digest().sha256Hex());
        stage("persist + reload bound graph inputs");

        // ---- 8. real worker registration + readiness (produces the grant authority evidence) ----
        registrationLoop.registerOnce();
        stage("readiness=" + jdbc.queryForList(
                "select environment_readiness, sandbox_readiness, snapshot_generation, "
                        + "(valid_until > now()) as fresh from wf_host_readiness"));
        stage("host_registration=" + jdbc.queryForList(
                "select physical_host_id, physical_host_incarnation_id, valid_until > now() as fresh "
                        + "from wf_host_registration"));
        stage("worker registration + readiness written");
        stage("authority=" + jdbc.queryForList(
                "select physical_host_id, physical_host_incarnation_id, current_generation "
                        + "from wf_host_snapshot_generation_authority"));
        stage("snapshot=" + jdbc.queryForList(
                "select snapshot_generation, cpu_millicores, memory_bytes, temporary_storage_bytes, "
                        + "safety_headroom_cpu_millicores, safety_headroom_memory_bytes, "
                        + "safety_headroom_temporary_storage_bytes from wf_host_resource_snapshot"));
        stage("reservations=" + jdbc.queryForList(
                "select count(*) as n_reservations, coalesce(sum(cpu_millicores),0) as cpu_from_"
                        + "reservations from wf_reservation"));
        var diagContribution = providerPluginCatalog.find(
                graph.tasks().getFirst().providerBindingPin()).orElseThrow();
        var diagDemand = TaskResourceDemandDeriver.derive(diagContribution.resourceProfile())
                .orElse(null);
        var diagAuthority = jdbc.queryForMap(
                "select physical_host_id, physical_host_incarnation_id, current_generation "
                        + "from wf_host_snapshot_generation_authority limit 1");
        stage("direct-assess demand=" + diagDemand + " -> " + reservationFeasibilityQuery.assess(
                PhysicalHostId.of((String) diagAuthority.get("physical_host_id")),
                PhysicalHostIncarnationId.of(
                        (String) diagAuthority.get("physical_host_incarnation_id")),
                new HostResourceSnapshotGeneration(
                        ((Number) diagAuthority.get("current_generation")).longValue()),
                diagDemand));
        try {
            stage("eligibility[evidence-then-now]=" + diagnoseEligibility(graph, diagContribution, false));
            stage("eligibility[minter-order: now-then-evidence]="
                    + diagnoseEligibility(graph, diagContribution, true));
        } catch (RuntimeException diagnosticFailure) {
            stage("eligibility-diagnostic-failed: " + diagnosticFailure);
        }

        // ---- 9. run the real workflow on the in-process Temporal test server (real worker) ----
        MediaTaskWorkflow workflow = workflowClient.newWorkflowStub(
                MediaTaskWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("render:" + TENANT + ":" + renderJobId)
                        .setTaskQueue("media-platform-tasks")
                        .build());
        MediaTaskWorkflowResult result;
        try {
            result = workflow.run(reference, TENANT);
        } catch (RuntimeException workflowFailure) {
            stage("workflow-failed: " + workflowFailure.getMessage());
            stage("graph_task_ids=" + graph.tasks().stream()
                    .map(t -> t.id().sha256Hex()).toList());
            stage("wf_task_ownership=" + jdbc.queryForList("select * from wf_task_ownership"));
            stage("wf_reservation=" + jdbc.queryForList(
                    "select reservation_id, task_id, state, cpu_millicores from wf_reservation"));
            stage("wf_execution_assignment=" + jdbc.queryForList(
                    "select assignment_id, task_id from wf_execution_assignment"));
            stage("wf_resolution=" + jdbc.queryForList(
                    "select request_work_id, result_kind, failure_reason, task_id, assignment_id "
                            + "from wf_request_work_resolution"));
            throw workflowFailure;
        }
        assertThat(result.status()).isEqualTo(MediaTaskWorkflowResult.STATUS_EXECUTED);
        stage("workflow EXECUTED tasks=" + result.executableTaskIds());

        // ---- 10. locate + read back the terminal render master artifact ----
        ExecutableTaskId terminalTaskId = terminalTaskId(graph);
        // P1-1A.11 (Option A2): matches TaskOutputPublicationPlanner's 64-char artifact id scheme.
        String finalArtifactId = "render-task-" + terminalTaskId.sha256Hex().substring(0, 52);
        Artifact finalArtifact = artifactQueryService
                .getArtifact(TENANT, new ArtifactId(finalArtifactId)).orElseThrow();
        assertThat(finalArtifact.state()).isEqualTo(ArtifactState.AVAILABLE);
        assertThat(finalArtifact.byteLength()).isGreaterThan(0L);
        byte[] outputBytes = readBack(finalArtifact);
        assertThat(outputBytes).isNotEmpty();
        assertThat(sha256Hex(outputBytes)).isEqualTo(finalArtifact.contentDigest().canonicalValue());
        assertThat(sha256Hex(outputBytes)).isNotEqualTo(sourceDigestHex);
        stage("artifact read-back " + finalArtifactId + " bytes=" + outputBytes.length);

        // ---- 11. independent FFprobe verification of the read-back bytes ----
        Path output = Files.write(WORK.resolve("verified-output.mp4"), outputBytes);
        Probe probe = ffprobe(output);
        assertThat(probe.codecName()).isEqualTo("h264");
        assertThat(probe.width()).isEqualTo(16);
        assertThat(probe.height()).isEqualTo(16);
        assertThat(probe.durationSeconds()).isGreaterThan(0.0);
        stage("ffprobe " + probe);
    }

    @SuppressWarnings("unchecked")
    private <T> T canonical(Object row, String methodName) {
        try {
            Method method = FirstRenderGrantMinter.class.getDeclaredMethod(methodName, row.getClass());
            method.setAccessible(true);
            return (T) method.invoke(null, row);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("diagnostic canonicalizer failed: " + methodName, failure);
        }
    }

    /**
     * Diagnostic only: reproduces CentralWorkMatcher.evaluateCanRun for the first task and reports the
     * RuntimeEligibilityDecision so the exact eligibility reason list is visible. It only observes.
     */
    private String diagnoseEligibility(
            ProviderBoundExecutableTaskGraph graph,
            com.example.platform.providerplugin.ProviderPluginContribution contribution,
            boolean minterOrdering) {
        var hostReg = registrationBoundary.findActiveHostRegistration().orElseThrow();
        var rtReg = registrationBoundary.findActiveRuntimeRegistration().orElseThrow();
        PhysicalHostId hostId = hostReg.physicalHostId();
        PhysicalHostIncarnationId hostInc = hostReg.physicalHostIncarnationId();
        WorkerRuntimeId rtId = rtReg.workerRuntimeId();
        WorkerRuntimeIncarnationId rtInc = rtReg.workerRuntimeIncarnationId();
        HostResourceSnapshotGeneration gen = registrationBoundary
                .currentSnapshotGeneration(hostId, hostInc).orElseThrow();
        WorkerReadinessEvidence readiness = registrationBoundary
                .findReadiness(hostId, hostInc).orElseThrow();
        var descriptorRow = registrationBoundary.findHostDescriptor(hostId).orElseThrow();
        var snapshotRow = registrationBoundary.findHostSnapshot(hostId, hostInc, gen).orElseThrow();

        HostResourceSnapshot snapshot = canonical(snapshotRow, "canonicalSnapshot");
        PhysicalHostDescriptor physicalHost = canonical(descriptorRow, "canonicalDescriptor");
        SafetyHeadroom headroom = new SafetyHeadroom(new ReservedResources(
                snapshotRow.safetyHeadroomCpuMillicores(),
                snapshotRow.safetyHeadroomMemoryBytes(),
                snapshotRow.safetyHeadroomTemporaryStorageBytes(), java.util.Map.of()));
        PhysicalHostAvailability hostAvail = new PhysicalHostAvailability(
                hostId, hostInc, AvailabilityState.REACHABLE);
        WorkerRuntimeAvailability rtAvail = new WorkerRuntimeAvailability(
                rtId, rtInc, AvailabilityState.REACHABLE);
        LocalWorkerRuntimeIncarnationBinding binding = new LocalWorkerRuntimeIncarnationBinding(
                rtId, rtInc, hostId, hostInc);
        AdmissionEvidenceProducer.Evidence evidence;
        java.time.Instant now;
        if (minterOrdering) {
            // Reproduce the minter's ordering: it captures `now` (line 149) BEFORE producing the
            // admission evidence (line 257), so observation.observedAt > evaluatedAt(now).
            now = clock.instant();
            evidence = admissionEvidenceProducer.produce(
                    rtId, hostId, contribution.providerBindingPin().providerImplementationId());
        } else {
            evidence = admissionEvidenceProducer.produce(
                    rtId, hostId, contribution.providerBindingPin().providerImplementationId());
            now = clock.instant();
        }
        WorkerRuntimeSupportAdvertisement adv = evidence.runtimeSupportAdvertisement().orElseThrow();
        WorkerRuntimeDescriptor rtDescriptor = WorkerRuntimeDescriptor.local(
                rtId, adv.runtimeKind(), hostId);
        HostResourceSnapshotFreshnessPolicy policy = new HostResourceSnapshotFreshnessPolicy(
                LocalHostRegistrationLoop.REGISTRATION_VALIDITY,
                HostResourceSnapshotSchemaVersion.CURRENT);
        SchedulableCapacity capacity = SchedulableCapacity.forLocalRuntime(
                snapshot, List.of(), headroom, hostAvail, rtAvail, binding, rtDescriptor, policy, now);
        RequestWork requestWork = new RequestWork(
                com.example.platform.workerfabric.domain.RequestWorkId.of("diag-" + now.toEpochMilli()),
                rtId, rtInc, hostId, hostInc, snapshot, rtAvail, java.util.Map.of(),
                readiness.environmentReadiness(), readiness.sandboxReadiness(),
                evidence.providerHardwareObservation(), evidence.runtimeDependencyObservation(),
                evidence.runtimeSupportAdvertisement(), Optional.empty());
        RequestWorkValidationContext context = new RequestWorkValidationContext(
                rtDescriptor, rtAvail, binding, physicalHost, hostAvail, snapshot, policy, now, capacity);

        ReservationFeasibility feasibility = reservationFeasibilityQuery.assess(
                hostId, hostInc, gen,
                TaskResourceDemandDeriver.derive(contribution.resourceProfile()).orElseThrow());
        PendingNativeWorkCandidate candidate = PendingNativeWorkProjection.project(
                graph.tasks().getFirst(), graph, contribution, feasibility);
        ExecutionBackendEligibilityDecision backendDecision = ExecutionBackendEligibilityEvaluator
                .evaluate(candidate.executableTask(), candidate.backendExecutionSupport(),
                        ExecutionBackend.NATIVE_PULL_WORKER);
        ExecutionBackendSelection backendSelection = ExecutionBackendSelection.select(
                graph, candidate.executableTask(), backendDecision);
        List<StaticProviderCompatibilityProof> proofs = candidate.executableTask().memberships().stream()
                .map(membership -> graph.providerFeasibilityView().requireStaticallyFeasible(
                        membership.physicalPlanUnit(),
                        candidate.staticallyCompatibleProviderCandidate()))
                .toList();
        NativeRuntimeEligibilityRequest eligibilityRequest = new NativeRuntimeEligibilityRequest(
                graph, candidate.executableTask(), candidate.staticallyCompatibleProviderCandidate(),
                proofs, candidate.providerHardwareRequirement(), candidate.runtimeDependencyRequirements(),
                evidence.providerHardwareObservation(), evidence.runtimeDependencyObservation(),
                backendSelection, Optional.of(rtDescriptor), Optional.of(rtAvail),
                Optional.of(binding), Optional.of(physicalHost), Optional.of(hostAvail),
                Optional.of(snapshot), policy, now, Optional.empty(), Optional.empty(),
                Optional.of(capacity), candidate.resourceDemand(),
                candidate.authoritativeReservationFeasibility(), readiness.environmentReadiness(),
                candidate.sandboxRequirement(), readiness.sandboxReadiness(),
                evidence.runtimeSupportAdvertisement(), candidate.runtimeSupportRequirement(),
                candidate.providerProbeRequirement(), candidate.providerProbeResult());
        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(eligibilityRequest);
        return "backend=" + backendDecision.status() + " candidateReservationFeasibility=" + feasibility
                + " eligibility=" + decision.status() + " reasons=" + decision.reasons();
    }

    private static ExecutableTaskId terminalTaskId(
            com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph graph) {
        java.util.Set<ExecutableTaskId> producers = new java.util.HashSet<>();
        graph.taskDependencies().forEach(d -> producers.add(d.producerTaskId()));
        return graph.tasks().stream()
                .map(ExecutableTask::id)
                .filter(id -> !producers.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no terminal task found"));
    }

    /**
     * P1-1A.14: reads the produced Artifact's bytes through the typed {@link StorageProvider} SPI —
     * the same Phase-16 read path the other closed-loop harnesses use. The provider is resolved from
     * the Artifact's own replica binding (never a hard-coded backend path), and the read uses
     * {@link IntegrityRequirement#VERIFY_DIGEST}; the byte length is checked against the Artifact.
     */
    private byte[] readBack(Artifact artifact) {
        ArtifactReplicaBinding replica = artifactQueryService
                .listReplicas(TENANT, artifact.artifactId()).getFirst();
        TenantContext.set(TENANT);
        try {
            StorageProvider provider = providerFor(replica.providerId());
            byte[] bytes = provider.openRead(new StorageReadRequest(
                            replica.storageObjectId(), Optional.empty(), IntegrityRequirement.VERIFY_DIGEST))
                    .orElseThrow(() -> new IllegalStateException(
                            "StorageProvider " + replica.providerId().value()
                                    + " could not read object " + replica.storageObjectId().value()))
                    .readAllBytes();
            if (bytes.length != artifact.byteLength()) {
                throw new IllegalStateException("read-back length " + bytes.length
                        + " does not match Artifact byteLength " + artifact.byteLength());
            }
            return bytes;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Artifact read-back failed", failure);
        } finally {
            TenantContext.clear();
        }
    }

    /** Resolves the exact registered StorageProvider for a replica's provider id (fail closed). */
    private StorageProvider providerFor(StorageProviderId providerId) {
        List<StorageProvider> matches = storageProviders.stream()
                .filter(candidate -> candidate.providerId().equals(providerId))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("expected exactly one StorageProvider for "
                    + providerId.value() + " but found " + matches.size());
        }
        return matches.getFirst();
    }

    private Probe ffprobe(Path file) throws Exception {
        Process p = new ProcessBuilder(FFPROBE.toString(), "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,width,height:format=duration",
                "-of", "default=noprint_wrappers=1", file.toString())
                .redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as(text).isZero();
        java.util.Map<String, String> values = new java.util.HashMap<>();
        text.lines().filter(l -> l.contains("=")).forEach(l -> {
            String[] kv = l.split("=", 2);
            values.put(kv[0], kv[1]);
        });
        return new Probe(values.get("codec_name"),
                Integer.parseInt(values.get("width")), Integer.parseInt(values.get("height")),
                Double.parseDouble(values.get("duration")));
    }

    private record Probe(String codecName, int width, int height, double durationSeconds) {}

    private static TimelineDocument document(String artifactId, String contentDigestHex) {
        TimelineClip clip = new TimelineClip(
                "clip-0", "asset-0", "stream-0", artifactId, contentDigestHex,
                MediaTime.ofMillis(0), MediaTime.ofMillis(1000),
                MediaTime.ofMillis(0), MediaTime.ofMillis(1000),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "v1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of());
    }

    private static StorageNamespace sourceNamespace() {
        return new StorageNamespace(TENANT, PROJECT, NamespaceClass.SOURCE,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path locatePluginJar() {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path root : List.of(base, base.getParent())) {
            if (root == null) continue;
            Path jar = root.resolve("ffmpeg-provider-module/build/libs/ffmpeg-provider-plugin-1.2.0.jar");
            if (Files.isRegularFile(jar)) {
                return jar;
            }
        }
        throw new IllegalStateException("ffmpeg-provider plugin JAR not found from " + base);
    }

    private static void stage(String label) {
        System.out.println("[FR-E2E] " + label);
    }
}
