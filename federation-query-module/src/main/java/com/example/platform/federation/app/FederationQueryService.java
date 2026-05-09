package com.example.platform.federation.app;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Federation query service — stub implementation.
 *
 * <p>This is a placeholder facade for future Calcite/Trino-based federated query execution.
 * The actual {@link com.example.platform.federation.domain.FederationQueryExecutor} SPI
 * is intentionally not wired here to avoid pulling in heavy query engine dependencies.</p>
 *
 * <p><strong>Future work:</strong> Apache Calcite or Trino integration will be added
 * when cross-source query requirements are finalized. Until then, this service
 * returns stub responses indicating the module is not active.</p>
 */
@Service
public class FederationQueryService {

    private static final String STUB_STATUS = "stub";

    public Map<String, Object> overview() {
        return Map.of(
                "module", "federation-query-module",
                "status", STUB_STATUS,
                "description", "联邦查询模块，预留 Calcite/Trino 等跨源查询接入点。"
        );
    }

    /**
     * Always returns a stub response. Real query execution requires a configured
     * {@link com.example.platform.federation.domain.FederationQueryExecutor} bean.
     */
    public Map<String, Object> execute(String query, java.util.List<String> sources) {
        return Map.of(
                "status", STUB_STATUS,
                "message", "Federated query execution is not implemented. Configure a FederationQueryExecutor bean to activate.",
                "query", query != null ? query : "",
                "sources", sources != null ? sources : java.util.List.of()
        );
    }

    public boolean isStub() {
        return true;
    }
}
