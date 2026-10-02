package com.example.platform.persistence.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.PhysicalExecutionPlanCanonicalCodec;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.ExecutionInputId;
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
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
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

/**
 * P2-5a2c: real-PostgreSQL proof for the {@code render_binding_inputs} migration and the jOOQ
 * bound-graph input store adapter — migration application, save/load round-trip, upsert-per-job,
 * and fail-closed digest verification. The plan/candidate fixture mirrors the bounded V1 codec
 * fixture in media-execution-plan-module.
 */
@Testcontainers
class JooqBoundGraphInputStoreIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final MediaTime ZERO = MediaTime.ofRational(0, 1);
    private static final MediaTime TWO = MediaTime.ofRational(2, 1);
    private static final FrameRate FPS = FrameRate.of(30, 1);

    private static DSLContext dsl;
    private static JooqBoundGraphInputStore store;

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
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        store = new JooqBoundGraphInputStore(dsl);
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
    void migrationCreatesRenderBindingInputsWithUniqueJobKeyAndPlanBytes() {
        assertTrue(columnExists("render_binding_inputs", "plan_json"));
        assertTrue(columnExists("render_binding_inputs", "candidates_json"));
        assertTrue(columnExists("render_binding_inputs", "declarations_json"));
        assertTrue(indexExists("uq_render_binding_inputs_job"));

        BoundGraphInputs inputs = inputsForBoundPlan("job-migration");
        store.save(inputs, "tenant-a", "job-migration");
        assertThrows(org.jooq.exception.DataAccessException.class, () -> dsl.execute(
                "insert into render_binding_inputs"
                        + "(tenant_id,render_job_id,plan_ref,plan_digest,expected_etg_digest,plan_json,candidates_json,declarations_json)"
                        + " values ('tenant-a','job-migration','ref','"
                        + "a".repeat(64) + "','" + "b".repeat(64) + "','\\x00','\\x00','\\x00')"));
    }

    @Test
    void saveThenLoadRoundTripsCanonicalInputs() {
        BoundGraphInputs inputs = inputsForBoundPlan("job-roundtrip");
        BoundGraphReference reference = store.save(inputs, "tenant-a", "job-roundtrip");

        assertEquals("render-binding-inputs/tenant-a/job-roundtrip", reference.planRef());
        assertEquals(PhysicalExecutionPlanCanonicalCodec.digestHex(inputs.physicalPlan()),
                reference.planDigest());

        BoundGraphInputs loaded = store.load(reference);
        assertEquals(inputs.physicalPlan().planId(), loaded.physicalPlan().planId());
        assertEquals(PhysicalExecutionPlanCanonicalCodec.digestHex(inputs.physicalPlan()),
                PhysicalExecutionPlanCanonicalCodec.digestHex(loaded.physicalPlan()));
        assertEquals(inputs.candidates(), loaded.candidates());
        assertEquals(inputs.transitionDeclarations(), loaded.transitionDeclarations());
        assertEquals(inputs.expectedExecutableTaskGraphDigest(),
                loaded.expectedExecutableTaskGraphDigest());
    }

    @Test
    void saveIsUpsertPerTenantAndJob() {
        BoundGraphInputs first = inputsForBoundPlan("job-upsert");
        store.save(first, "tenant-a", "job-upsert");
        BoundGraphInputs second = inputsForBoundPlan("job-upsert-v2");
        BoundGraphReference reference = store.save(second, "tenant-a", "job-upsert");

        assertEquals(1, dsl.fetchCount(DSL.table("render_binding_inputs"),
                DSL.field("tenant_id").eq("tenant-a").and(DSL.field("render_job_id").eq("job-upsert"))));
        assertEquals(second.physicalPlan().planId(), store.load(reference).physicalPlan().planId());
    }

    @Test
    void loadFailsClosedWhenStoredPlanBytesDoNotMatchTheReferenceDigest() {
        BoundGraphInputs inputs = inputsForBoundPlan("job-tamper");
        BoundGraphReference reference = store.save(inputs, "tenant-a", "job-tamper");

        // Replace the persisted plan bytes with a different valid plan: the reference digest no
        // longer matches the re-decoded plan, so load must fail closed.
        byte[] differentPlan = PhysicalExecutionPlanCanonicalCodec.encode(
                inputsForBoundPlan("job-tamper-other").physicalPlan());
        dsl.execute("update render_binding_inputs set plan_json = ? "
                + "where tenant_id='tenant-a' and render_job_id='job-tamper'", differentPlan);

        assertThrows(BoundGraphDigestMismatchException.class, () -> store.load(reference));
    }

    @Test
    void loadRejectsUnknownReference() {
        BoundGraphReference reference = new BoundGraphReference(
                "tenant-a", "job-missing", "render-binding-inputs/tenant-a/job-missing",
                "a".repeat(64), "b".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> store.load(reference));
    }

    private boolean columnExists(String table, String column) {
        return dsl.fetchCount(DSL.table("information_schema.columns"),
                DSL.field("table_name").eq(table).and(DSL.field("column_name").eq(column))) > 0;
    }

    private boolean indexExists(String indexName) {
        return dsl.fetchCount(DSL.table("pg_indexes"), DSL.field("indexname").eq(indexName)) > 0;
    }

    // ---------- fixture (mirrors media-execution-plan bounded V1 codec fixture) ----------

    private static BoundGraphInputs inputsForBoundPlan(String planId) {
        PhysicalExecutionPlan plan = plan(planId);
        ProviderCandidate candidate = candidate("provider-a");
        // The store only persists/returns the digest; re-derivation is BoundGraphRederivation's job.
        return new BoundGraphInputs(
                plan,
                List.of(candidate),
                List.of(),
                new com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest("c".repeat(64)));
    }

    private static PhysicalExecutionPlan plan(String planId) {
        InputBinding input = new InputBinding(
                new ExecutionInputId("input-a"),
                "logical-unit-a",
                new ExecutionStepId("unit-a"),
                new RenderNodeId("render-unit-a"),
                null, null, null, null,
                new RenderArtifactReference.SourceArtifact(
                        new ArtifactId("art-source"), ContentDigest.sha256("a".repeat(64))),
                new RenderSampleWindow(ZERO, TWO, FPS));
        OutputDeclaration output = new OutputDeclaration(
                new ExecutionOutputId("output-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(),
                List.of(),
                List.of(new RenderArtifactReference.FinalArtifactExpectation(RenderOutputRole.RENDER_MASTER)));
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(input),
                List.of(output),
                List.of(),
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                List.of(new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of("video.decode"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0))))),
                List.of(new ExecutionIntentRef(new RenderExecutionRequirement(
                        RenderExecutionRequirement.GpuRequirement.NONE,
                        RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        true))),
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
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT),
                        List.of(),
                        List.of(com.example.platform.execution.compatibility.StaticCompatibilityConstraint
                                .ProviderDeviceKind.CPU),
                        List.of(),
                        List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),
                        List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                        List.of(BoundaryContractId.of("test.boundary.v1")),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
}
