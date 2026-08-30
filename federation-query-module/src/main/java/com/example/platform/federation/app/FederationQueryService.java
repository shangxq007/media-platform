package com.example.platform.federation.app;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fail-closed federation query boundary.
 *
 * <p>This is a placeholder facade for future Calcite/Trino-based federated query execution.
 * The actual {@link com.example.platform.federation.domain.FederationQueryExecutor} SPI
 * is intentionally not wired here to avoid pulling in heavy query engine dependencies.</p>
 *
 * <p>Apache Calcite or Trino integration is not composed yet, so no successful
 * placeholder response is permitted.</p>
 */
@Service
public class FederationQueryService {

    public Map<String, Object> overview() {
        throw new FederationQueryUnavailableException();
    }

    /**
     * Real query execution requires a configured
     * {@link com.example.platform.federation.domain.FederationQueryExecutor} bean.
     */
    public Map<String, Object> execute(String query, java.util.List<String> sources) {
        throw new FederationQueryUnavailableException();
    }
}
