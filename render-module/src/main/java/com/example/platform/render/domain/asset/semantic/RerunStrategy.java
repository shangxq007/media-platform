package com.example.platform.render.domain.asset.semantic;

/**
 * Controls how AI enrichment handles repeated execution.
 */
public enum RerunStrategy {
    /** Always rerun, regardless of existing results. */
    FORCE,
    /** Rerun if existing result is missing, failed, or provider/model changed. */
    SMART,
    /** Skip if any completed result exists for this capability. */
    SKIP_IF_EXISTS,
    /** Rerun only if the provider has changed. */
    PROVIDER_UPGRADE,
    /** Rerun only if the model has changed. */
    MODEL_UPGRADE
}
