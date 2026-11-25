package com.example.platform.persistence.binding;

import static com.example.platform.typedschema.jooq.generated.render.tables.RenderBindingInputs.RENDER_BINDING_INPUTS;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.PhysicalExecutionPlanCanonicalCodec;
import com.example.platform.execution.binding.ProviderBoundaryCompatibilityDeclarationCanonicalCodec;
import com.example.platform.execution.binding.BoundGraphInputsCanonicalCodec;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Composition-root adapter for {@link BoundGraphInputStore} over the canonical
 * {@code render_binding_inputs} table (P2-5a2c).
 *
 * <p>The plan and selection are independently versioned canonical frames. The selection includes
 * the policy and the full sorted catalog. Transition declarations are separately framed in Base64.
 * The adapter verifies both stored SHA-256 digests and rederives the expected graph on restore.
 */
@Repository
public class JooqBoundGraphInputStore implements BoundGraphInputStore {

    private static final String PLAN_REF_PREFIX = "render-binding-inputs/";

    private final DSLContext dsl;

    public JooqBoundGraphInputStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public BoundGraphReference save(BoundGraphInputs inputs, String tenantId, String renderJobId) {
        String planRef = PLAN_REF_PREFIX + tenantId + "/" + renderJobId;
        byte[] planJson = PhysicalExecutionPlanCanonicalCodec.encode(inputs.physicalPlan());
        String planDigest = PhysicalExecutionPlanCanonicalCodec.digestHex(inputs.physicalPlan());
        String etgDigest = inputs.expectedExecutableTaskGraphDigest().sha256Hex();
        byte[] candidatesJson = BoundGraphInputsCanonicalCodec.encodeSelection(inputs);
        byte[] declarationsJson = encodeDeclarations(inputs.transitionDeclarations());
        String selectionDigest = sha256(candidatesJson);

        dsl.insertInto(RENDER_BINDING_INPUTS)
                .columns(RENDER_BINDING_INPUTS.TENANT_ID, RENDER_BINDING_INPUTS.RENDER_JOB_ID,
                        RENDER_BINDING_INPUTS.PLAN_REF, RENDER_BINDING_INPUTS.PLAN_DIGEST,
                        RENDER_BINDING_INPUTS.EXPECTED_ETG_DIGEST, RENDER_BINDING_INPUTS.PLAN_JSON,
                        RENDER_BINDING_INPUTS.CANDIDATES_JSON, RENDER_BINDING_INPUTS.DECLARATIONS_JSON,
                        RENDER_BINDING_INPUTS.CANDIDATE_SET_DIGEST, RENDER_BINDING_INPUTS.CREATED_AT)
                .values(tenantId, renderJobId, planRef, planDigest, etgDigest, planJson,
                        candidatesJson, declarationsJson, selectionDigest, Instant.now())
                .onConflict(RENDER_BINDING_INPUTS.TENANT_ID, RENDER_BINDING_INPUTS.RENDER_JOB_ID)
                .doNothing()
                .execute();
        var frozen = dsl.selectFrom(RENDER_BINDING_INPUTS)
                .where(RENDER_BINDING_INPUTS.TENANT_ID.eq(tenantId))
                .and(RENDER_BINDING_INPUTS.RENDER_JOB_ID.eq(renderJobId)).fetchSingle();
        if (!selectionDigest.equals(frozen.getCandidateSetDigest()) || !planRef.equals(frozen.getPlanRef()) || !planDigest.equals(frozen.getPlanDigest())
                || !etgDigest.equals(frozen.getExpectedEtgDigest())
                || !java.util.Arrays.equals(planJson, frozen.getPlanJson())
                || !java.util.Arrays.equals(candidatesJson, frozen.getCandidatesJson())
                || !java.util.Arrays.equals(declarationsJson, frozen.getDeclarationsJson()))
            throw new IllegalStateException("Bound graph already frozen for this Job");

        return new BoundGraphReference(tenantId, renderJobId, planRef, planDigest, etgDigest);
    }

    @Override
    public java.util.Optional<BoundGraphReference> findReference(String tenantId, String renderJobId) {
        return dsl.selectFrom(RENDER_BINDING_INPUTS)
                .where(RENDER_BINDING_INPUTS.TENANT_ID.eq(tenantId))
                .and(RENDER_BINDING_INPUTS.RENDER_JOB_ID.eq(renderJobId)).fetchOptional()
                .map(r -> new BoundGraphReference(tenantId, renderJobId, r.getPlanRef(), r.getPlanDigest(), r.getExpectedEtgDigest()));
    }

    @Override
    public BoundGraphInputs load(BoundGraphReference reference) {
        Record row = dsl.selectFrom(RENDER_BINDING_INPUTS)
                .where(RENDER_BINDING_INPUTS.TENANT_ID.eq(reference.tenantId()))
                .and(RENDER_BINDING_INPUTS.RENDER_JOB_ID.eq(reference.renderJobId()))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException(
                    "no bound-graph inputs stored for " + reference.planRef());
        }

        if (!reference.planRef().equals(row.get(RENDER_BINDING_INPUTS.PLAN_REF))) {
            throw new IllegalArgumentException("Stored plan reference does not match the requested binding");
        }
        byte[] planJson = row.get(RENDER_BINDING_INPUTS.PLAN_JSON);
        String storedPlanDigest = row.get(RENDER_BINDING_INPUTS.PLAN_DIGEST);

        PhysicalExecutionPlan plan = PhysicalExecutionPlanCanonicalCodec.decode(planJson);
        String actualPlanDigest = sha256(planJson);
        if (!java.util.Arrays.equals(planJson, PhysicalExecutionPlanCanonicalCodec.encode(plan))) {
            throw new IllegalStateException("Noncanonical physical plan bytes");
        }
        if (!actualPlanDigest.equals(reference.planDigest())
                || !actualPlanDigest.equals(storedPlanDigest)) {
            throw new BoundGraphDigestMismatchException(reference.planDigest(), actualPlanDigest);
        }

        String storedEtgDigest = row.get(RENDER_BINDING_INPUTS.EXPECTED_ETG_DIGEST);
        if (!storedEtgDigest.equals(reference.expectedExecutableTaskGraphDigest())) {
            throw new BoundGraphDigestMismatchException(
                    reference.expectedExecutableTaskGraphDigest(), storedEtgDigest);
        }

        byte[] candidatesJson = row.get(RENDER_BINDING_INPUTS.CANDIDATES_JSON);
        var selection = BoundGraphInputsCanonicalCodec.decodeSelection(candidatesJson);
        var inputs = new BoundGraphInputs(
                plan,
                selection.candidates(),
                decodeDeclarations(row.get(RENDER_BINDING_INPUTS.DECLARATIONS_JSON)),
                new ExecutableTaskGraphDigest(storedEtgDigest), selection.policy());
        String actualSelectionDigest = sha256(candidatesJson);
        if (!actualSelectionDigest.equals(row.get(RENDER_BINDING_INPUTS.CANDIDATE_SET_DIGEST))) {
            throw new IllegalStateException("Frozen provider selection checksum mismatch");
        }
        return inputs;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] encodeDeclarations(List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        List<String> frames = new ArrayList<>(declarations.size());
        for (ProviderBoundaryCompatibilityDeclaration declaration : declarations) {
            frames.add(Base64.getEncoder()
                    .encodeToString(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(declaration)));
        }
        frames.sort(String::compareTo);
        return join(frames);
    }

    private static List<ProviderBoundaryCompatibilityDeclaration> decodeDeclarations(byte[] payload) {
        List<ProviderBoundaryCompatibilityDeclaration> declarations = new ArrayList<>();
        for (String frame : split(payload)) {
            declarations.add(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.decode(
                    Base64.getDecoder().decode(frame)));
        }
        return List.copyOf(declarations);
    }

    private static byte[] join(List<String> frames) {
        return String.join("\n", frames).getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> split(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split("\n", -1));
    }
}
