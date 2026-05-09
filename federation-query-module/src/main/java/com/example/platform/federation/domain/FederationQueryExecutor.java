package com.example.platform.federation.domain;

import java.util.List;
import java.util.Map;

/**
 * SPI interface for federated query execution across multiple data sources.
 *
 * <p>Implementations may use Apache Calcite, Trino, or other distributed
 * query engines to execute queries across heterogeneous sources.</p>
 *
 * <p><strong>Future work:</strong> Calcite/Trino integration is planned but not yet available.
 * This interface provides the contract for pluggable query backends.</p>
 */
public interface FederationQueryExecutor {

    /**
     * Executes a federated query across the specified sources.
     *
     * @param query   the query string (SQL or engine-specific dialect)
     * @param sources the list of source identifiers to query against
     * @return the query result containing rows and metadata
     */
    FederationQueryResult execute(String query, List<String> sources);

    /**
     * Result of a federated query execution.
     */
    record FederationQueryResult(int rowCount, List<Map<String, Object>> rows, String status) {}
}
