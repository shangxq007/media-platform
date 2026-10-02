package com.example.platform.runtime.mediatask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.persistence.binding.JooqBoundGraphInputStore;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import java.time.Clock;

/**
 * P2-5b-1-R2 integration: bound-graph canonical persistence (V1 + V2 migrations on real PostgreSQL)
 * followed by the preparation half of the media task activity.
 *
 * <p>Chain exercised: P2-3 binding → {@code JooqBoundGraphInputStore.save} → {@code load} →
 * {@code BoundGraphRederivation.rederive} → digest verification. No Temporal and no whole-graph
 * execution (P2-5b-2).
 */
@Testcontainers
class MediaTaskActivityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String TENANT = "tenant-a";
    private static final String JOB = "job-approved";
    private static final MediaTime ZERO = MediaTime.ofRational(0, 1);
    private static final MediaTime TWO = MediaTime.ofRational(2, 1);
    private static final FrameRate FPS = FrameRate.of(30, 1);

    private static JooqBoundGraphInputStore store;
    private static MediaTaskActivity activity;

    @BeforeAll
    static void setup() throws Exception {
        PG.start();
        Path migrationDir = locateMigrationDir();
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V1__initial_schema.sql")),
                "/migrations/V1.sql");
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V2__render_binding_inputs.sql")),
                "/migrations/V2.sql");
        applyMigration("/migrations/V1.sql");
        applyMigration("/migrations/V2.sql");
        DSLContext dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        store = new JooqBoundGraphInputStore(dsl);
        // Preparation-only test: the execution collaborators stay unused doubles.
        activity = new MediaTaskActivity(
                store,
                mock(AtomicAssignmentGrantBoundary.class),
                mock(RuntimeClosedLoopOrchestrator.class),
                new MediaTaskPublicationSettings(
                        "project-1", "provider-1", "local",
                        ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, Clock.systemUTC()));
    }

    private static void applyMigration(String path) throws Exception {
        var result = PG.execInContainer("psql", "-U", PG.getUsername(), "-d", PG.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-f", path);
        assertEquals(0, result.getExitCode(), "migration " + path + " failed: " + result.getStderr());
    }

    private static Path locateMigrationDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("platform-app/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate db/migration directory");
    }

    @Test
    void savedBoundGraphSurvivesLoadRederivationAndPreparation() {
        PhysicalExecutionPlan plan = plan("p2-5b-integration");
        ProviderCandidate candidate = candidate("provider-a");
        var bound = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        BoundGraphInputs inputs = new BoundGraphInputs(
                plan, List.of(candidate), List.of(), bound.digest());

        BoundGraphReference reference = store.save(inputs, TENANT, JOB);
        PreparedTask prepared = activity.prepareTask(reference, TENANT);

        assertEquals(bound.digest(), prepared.executableTaskGraphDigest());
        assertEquals(bound.digest(), prepared.executableTaskGraph().digest());
        assertEquals(1, prepared.executableTaskGraph().tasks().size());
        assertEquals(bound.digest(),
                prepared.boundGraphInputs().expectedExecutableTaskGraphDigest());
        assertEquals("render-binding-inputs/" + TENANT + "/" + JOB, reference.planRef());
    }

    @Test
    void tamperedReferenceDigestFailsClosedAtPreparation() {
        PhysicalExecutionPlan plan = plan("p2-5b-tampered");
        ProviderCandidate candidate = candidate("provider-a");
        var bound = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        BoundGraphReference reference = store.save(
                new BoundGraphInputs(plan, List.of(candidate), List.of(), bound.digest()),
                TENANT, "job-tampered");

        BoundGraphReference tampered = new BoundGraphReference(
                reference.tenantId(), reference.renderJobId(), reference.planRef(),
                reference.planDigest(), "0".repeat(64));

        assertThrows(BoundGraphDigestMismatchException.class,
                () -> activity.prepareTask(tampered, TENANT));
    }

    @Test
    void unknownJobFailsClosedAtPreparation() {
        BoundGraphReference missing = new BoundGraphReference(
                TENANT, "job-absent", "render-binding-inputs/" + TENANT + "/job-absent",
                "a".repeat(64), "b".repeat(64));
        assertThrows(IllegalArgumentException.class,
                () -> activity.prepareTask(missing, TENANT));
    }

    // ---------- fixture (codec-encodable and bindable) ----------

    private static PhysicalExecutionPlan plan(String planId) {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-1"),
                        "logical-unit-a",
                        new RenderNodeId("render-unit-a"),
                        List.of(), List.of(), List.of(), List.of())),
                List.of(),
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                List.of(),
                List.of(),
                new RenderExtent(ZERO, TWO, FPS),
                true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId(planId),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-" + planId),
                List.of(unit),
                new RenderExtent(ZERO, TWO, FPS),
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        return new ProviderCandidate(
                binding,
                new ProviderDescriptor(providerId, implementationId, version, contractVersion, profileReference),
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
}
