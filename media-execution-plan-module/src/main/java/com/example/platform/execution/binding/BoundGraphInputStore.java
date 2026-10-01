package com.example.platform.execution.binding;

/**
 * Durable store for the inputs of a provider-bound executable task graph.
 *
 * <p>Port only: the owning module has no persistence infrastructure, so the store is
 * implemented by the composition root (an adapter over the canonical database) while
 * this module keeps the contract, the canonical codecs and the digest rules.
 *
 * <p>Both operations are tenant/job scoped. {@code save} derives the durable reference
 * (including the canonical plan digest) from the encoded inputs; {@code load} verifies
 * that digest against the stored bytes and fails closed on any divergence.
 */
public interface BoundGraphInputStore {

    /**
     * Persists the bound-graph inputs and returns their stable reference.
     *
     * @throws IllegalStateException when a record already exists for the same tenant/job
     */
    BoundGraphReference save(BoundGraphInputs inputs, String tenantId, String renderJobId);

    /**
     * Loads the inputs addressed by the reference.
     *
     * @throws BoundGraphDigestMismatchException when the stored plan bytes do not digest to the reference
     * @throws IllegalArgumentException          when no record exists for the reference
     */
    BoundGraphInputs load(BoundGraphReference reference);
}
