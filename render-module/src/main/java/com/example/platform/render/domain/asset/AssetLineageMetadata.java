package com.example.platform.render.domain.asset;

import java.util.List;

/**
 * Lightweight asset lineage metadata fields.
 *
 * <p>Expresses processing provenance without depending on OpenLineage SDK.
 * Fields map to the {@code artifact_node} lineage columns.</p>
 *
 * @param sourceAssetId       primary source asset identifier
 * @param derivedFromAssetIds list of upstream asset IDs this asset was derived from
 * @param workflowId          workflow that produced this asset
 * @param runId               specific run identifier
 * @param operatorId          operator (user or system) that produced this asset
 * @param parametersHash      hash of processing parameters for reproducibility
 */
public record AssetLineageMetadata(
        String sourceAssetId,
        List<String> derivedFromAssetIds,
        String workflowId,
        String runId,
        String operatorId,
        String parametersHash) {

    public static AssetLineageMetadata empty() {
        return new AssetLineageMetadata(null, List.of(), null, null, null, null);
    }
}
