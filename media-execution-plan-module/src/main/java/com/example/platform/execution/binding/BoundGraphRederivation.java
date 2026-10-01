package com.example.platform.execution.binding;

import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Objects;

/**
 * Deterministic re-derivation of a provider-bound executable task graph from its
 * durable inputs.
 *
 * <p>Pure and stateless: the graph is rebuilt through the SAME guarded #22 binding
 * entry used at planning time, so re-derivation cannot drift from the original
 * binding semantics. The re-derived graph is accepted only when its semantic digest
 * equals the expected digest recorded with the durable inputs; anything else fails
 * closed and the work is not executed.
 *
 * <p>This is the worker-side seam: a durable activity loads {@link BoundGraphInputs}
 * from a {@link BoundGraphReference}, calls this method, and executes the returned
 * graph through the runtime closed loop.
 */
public final class BoundGraphRederivation {

    private BoundGraphRederivation() {
    }

    /**
     * Re-derives the bound graph and verifies it against the recorded digest.
     *
     * @throws ProviderBindingException           when the plan is not bindable (propagated from #22)
     * @throws BoundGraphDigestMismatchException  when the re-derived digest differs from the expected one
     */
    public static ProviderBoundExecutableTaskGraph rederive(BoundGraphInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        ProviderBoundExecutableTaskGraph graph = new ProviderBindingEntryService()
                .bind(
                        inputs.physicalPlan(),
                        inputs.candidates(),
                        inputs.transitionDeclarations())
                .executableTaskGraph();
        String expected = inputs.expectedExecutableTaskGraphDigest().sha256Hex();
        String actual = graph.digest().sha256Hex();
        if (!expected.equals(actual)) {
            throw new BoundGraphDigestMismatchException(expected, actual);
        }
        return graph;
    }
}
