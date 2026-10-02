package com.example.platform.persistence.binding;

import static com.example.platform.typedschema.jooq.generated.tables.RenderBindingInputs.RENDER_BINDING_INPUTS;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.PhysicalExecutionPlanCanonicalCodec;
import com.example.platform.execution.binding.ProviderBoundaryCompatibilityDeclarationCanonicalCodec;
import com.example.platform.execution.binding.ProviderCandidateCanonicalCodec;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
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
 * <p>The owning module (media-execution-plan) has no persistence infrastructure and keeps only
 * the port, the bounded V1 canonical codecs and the digest rules. This adapter encodes the
 * {@link PhysicalExecutionPlan} with {@link PhysicalExecutionPlanCanonicalCodec} and stores one
 * canonical frame per candidate/declaration, addressed by {@code (tenant_id, render_job_id)}.
 *
 * <p>Container format for the repeated rows: the canonical codec bytes of each element are
 * Base64-encoded and joined with {@code '\n'} (Base64 never contains a newline), preserving list
 * order. The plan is stored as its exact canonical bytes; {@code planDigest} is the lowercase
 * SHA-256 of those bytes and is re-verified on load.
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
        byte[] candidatesJson = encodeCandidates(inputs.candidates());
        byte[] declarationsJson = encodeDeclarations(inputs.transitionDeclarations());

        dsl.insertInto(RENDER_BINDING_INPUTS)
                .columns(RENDER_BINDING_INPUTS.TENANT_ID, RENDER_BINDING_INPUTS.RENDER_JOB_ID,
                        RENDER_BINDING_INPUTS.PLAN_REF, RENDER_BINDING_INPUTS.PLAN_DIGEST,
                        RENDER_BINDING_INPUTS.EXPECTED_ETG_DIGEST, RENDER_BINDING_INPUTS.PLAN_JSON,
                        RENDER_BINDING_INPUTS.CANDIDATES_JSON, RENDER_BINDING_INPUTS.DECLARATIONS_JSON,
                        RENDER_BINDING_INPUTS.CREATED_AT)
                .values(tenantId, renderJobId, planRef, planDigest, etgDigest, planJson,
                        candidatesJson, declarationsJson, Instant.now())
                .onConflict(RENDER_BINDING_INPUTS.TENANT_ID, RENDER_BINDING_INPUTS.RENDER_JOB_ID)
                .doUpdate()
                .set(RENDER_BINDING_INPUTS.PLAN_REF, planRef)
                .set(RENDER_BINDING_INPUTS.PLAN_DIGEST, planDigest)
                .set(RENDER_BINDING_INPUTS.EXPECTED_ETG_DIGEST, etgDigest)
                .set(RENDER_BINDING_INPUTS.PLAN_JSON, planJson)
                .set(RENDER_BINDING_INPUTS.CANDIDATES_JSON, candidatesJson)
                .set(RENDER_BINDING_INPUTS.DECLARATIONS_JSON, declarationsJson)
                .set(RENDER_BINDING_INPUTS.CREATED_AT, Instant.now())
                .execute();

        return new BoundGraphReference(tenantId, renderJobId, planRef, planDigest, etgDigest);
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

        byte[] planJson = row.get(RENDER_BINDING_INPUTS.PLAN_JSON);
        String storedPlanDigest = row.get(RENDER_BINDING_INPUTS.PLAN_DIGEST);

        PhysicalExecutionPlan plan = PhysicalExecutionPlanCanonicalCodec.decode(planJson);
        String actualPlanDigest = PhysicalExecutionPlanCanonicalCodec.digestHex(plan);
        if (!actualPlanDigest.equals(reference.planDigest())
                || !actualPlanDigest.equals(storedPlanDigest)) {
            throw new BoundGraphDigestMismatchException(reference.planDigest(), actualPlanDigest);
        }

        String storedEtgDigest = row.get(RENDER_BINDING_INPUTS.EXPECTED_ETG_DIGEST);
        if (!storedEtgDigest.equals(reference.expectedExecutableTaskGraphDigest())) {
            throw new BoundGraphDigestMismatchException(
                    reference.expectedExecutableTaskGraphDigest(), storedEtgDigest);
        }

        return new BoundGraphInputs(
                plan,
                decodeCandidates(row.get(RENDER_BINDING_INPUTS.CANDIDATES_JSON)),
                decodeDeclarations(row.get(RENDER_BINDING_INPUTS.DECLARATIONS_JSON)),
                new ExecutableTaskGraphDigest(storedEtgDigest));
    }

    private static byte[] encodeCandidates(List<ProviderCandidate> candidates) {
        List<String> frames = new ArrayList<>(candidates.size());
        for (ProviderCandidate candidate : candidates) {
            frames.add(Base64.getEncoder().encodeToString(ProviderCandidateCanonicalCodec.encode(candidate)));
        }
        return join(frames);
    }

    private static List<ProviderCandidate> decodeCandidates(byte[] payload) {
        List<ProviderCandidate> candidates = new ArrayList<>();
        for (String frame : split(payload)) {
            candidates.add(ProviderCandidateCanonicalCodec.decode(Base64.getDecoder().decode(frame)));
        }
        return List.copyOf(candidates);
    }

    private static byte[] encodeDeclarations(List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        List<String> frames = new ArrayList<>(declarations.size());
        for (ProviderBoundaryCompatibilityDeclaration declaration : declarations) {
            frames.add(Base64.getEncoder()
                    .encodeToString(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(declaration)));
        }
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
