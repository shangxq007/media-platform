package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.BoundGraphRederivation;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Objects;

/**
 * P2-5b-1-R2 preparation half of the per-graph media task activity.
 *
 * <p><b>Test-wired only.</b> This class deliberately carries no Spring stereotype: it is not
 * registered in any production context (same discipline as the P2-1/P2-2/P2-3/P2-5a entry points).
 * Production registration, the worker role and the catalog-derived runtime binding map belong to
 * P2-5b-2 / P2-6.
 *
 * <p>{@link #prepareTask} performs exactly three steps, all fail-closed, and never repairs anything:
 * <ol>
 *   <li>{@link BoundGraphInputStore#load} — canonical, digest-verified rehydration of the bound inputs;</li>
 *   <li>{@link BoundGraphRederivation#rederive} — deterministic re-derivation of the executable task graph;</li>
 *   <li>digest re-verification — the re-derived graph digest must equal both the stored expectation and
 *       the caller's {@link BoundGraphReference} expectation.</li>
 * </ol>
 *
 * <p>Whole-graph execution ({@code executeTask}) is intentionally absent: it requires the platform
 * attempt/generation construction and the catalog binding map owned by P2-5b-2.
 */
public final class MediaTaskActivity {

    private final BoundGraphInputStore boundGraphInputStore;

    public MediaTaskActivity(BoundGraphInputStore boundGraphInputStore) {
        this.boundGraphInputStore = Objects.requireNonNull(
                boundGraphInputStore, "boundGraphInputStore");
    }

    /**
     * Loads, re-derives and digest-verifies the bound graph addressed by {@code reference}.
     *
     * @throws IllegalArgumentException          when the tenant scope does not match, or no record
     *                                           exists for the reference
     * @throws BoundGraphDigestMismatchException when the stored or re-derived digest diverges
     */
    public PreparedTask prepareTask(BoundGraphReference reference, String tenantId) {
        Objects.requireNonNull(reference, "reference");
        requireMatchingTenant(reference, tenantId);

        BoundGraphInputs inputs = boundGraphInputStore.load(reference);

        ProviderBoundExecutableTaskGraph graph = BoundGraphRederivation.rederive(inputs);

        String rederived = graph.digest().sha256Hex();
        if (!rederived.equals(reference.expectedExecutableTaskGraphDigest())) {
            throw new BoundGraphDigestMismatchException(
                    reference.expectedExecutableTaskGraphDigest(), rederived);
        }
        String storedExpectation = inputs.expectedExecutableTaskGraphDigest().sha256Hex();
        if (!rederived.equals(storedExpectation)) {
            throw new BoundGraphDigestMismatchException(storedExpectation, rederived);
        }
        return new PreparedTask(graph, inputs, graph.digest());
    }

    private static void requireMatchingTenant(BoundGraphReference reference, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!tenantId.equals(reference.tenantId())) {
            throw new IllegalArgumentException(
                    "tenantId must match the bound graph reference scope");
        }
    }
}
