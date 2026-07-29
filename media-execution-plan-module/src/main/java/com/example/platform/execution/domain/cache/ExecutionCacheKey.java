package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Cache key for execution plans.
 *
 * <p>Based on: plan digest, step digests, input digests, operation version, determinism classification.
 * Non-deterministic plans are rejected from caching.
 *
 * <p>Immutable value object.
 */
public record ExecutionCacheKey(
        String key,
        ExecutionPlanDigest planDigest,
        ExecutionDeterminism determinism
) implements Serializable {

    public ExecutionCacheKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(planDigest, "planDigest");
        Objects.requireNonNull(determinism, "determinism");
        if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
    }

    /**
     * Creates a cache key from a plan.
     *
     * @throws ExecutionPlanDomainException if the plan is non-deterministic
     */
    public static ExecutionCacheKey fromPlan(MediaExecutionPlan plan) {
        if (plan.steps().stream().anyMatch(s -> !s.isDeterministic())) {
            throw new ExecutionPlanDomainException(
                    ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_NON_DETERMINISTIC_CACHE_KEY)
                            .planId(plan.planId().value())
                            .detail("Cannot cache non-deterministic plan")
                            .build());
        }

        StringBuilder sb = new StringBuilder("cacheKey{");
        sb.append("planDigest=").append(plan.digest().value());
        sb.append(",schema=").append(plan.schemaVersion().value());
        sb.append(",rev=").append(plan.timelineRevisionId());

        // Include step digests for per-step caching
        sb.append(",steps=[");
        plan.steps().stream()
                .map(s -> ExecutionPlanCanonicalSerializer.digestStep(s))
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        // Include input digests for cache invalidation on input change
        sb.append(",inputs=[");
        plan.inputs().stream()
                .map(ExecutionInputBinding::expectedContentDigest)
                .map(d -> d.canonicalValue())
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append('}');

        String hash = ExecutionPlanCanonicalSerializer.sha256Hex(sb.toString());
        return new ExecutionCacheKey(hash, plan.digest(), ExecutionDeterminism.DETERMINISTIC);
    }

    /**
     * Returns true if this cache key is valid for the given plan.
     */
    public boolean matches(MediaExecutionPlan plan) {
        ExecutionCacheKey other = fromPlan(plan);
        return this.key.equals(other.key);
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "cacheKey{" +
                "key=" + key +
                ",digest=" + planDigest.value() +
                ",det=" + determinism.name() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
