package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryCostEstimate;
import com.example.platform.federation.nlq.domain.QueryResult;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SqlCostEstimator sqlCostEstimator;
    private final ResultRedactionService resultRedactionService;
    private final ResultSummarizer resultSummarizer;
    private final ChartSuggestionService chartSuggestionService;

    public QueryExecutionService(JdbcTemplate jdbcTemplate, SqlCostEstimator sqlCostEstimator,
            ResultRedactionService resultRedactionService, ResultSummarizer resultSummarizer,
            ChartSuggestionService chartSuggestionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.sqlCostEstimator = sqlCostEstimator;
        this.resultRedactionService = resultRedactionService;
        this.resultSummarizer = resultSummarizer;
        this.chartSuggestionService = chartSuggestionService;
    }

    public QueryResult execute(String sql, Map<String, Object> parameters, int maxRows, int timeoutSeconds) {
        String queryId = Ids.newId("qry");
        long start = System.currentTimeMillis();

        log.info("QueryExecutionService: executing queryId={}, maxRows={}, timeout={}s", queryId, maxRows, timeoutSeconds);

        QueryCostEstimate estimate = sqlCostEstimator.estimate(sql);
        int effectiveTimeout = sqlCostEstimator.getTimeoutSeconds(estimate);

        jdbcTemplate.setQueryTimeout(effectiveTimeout);
        jdbcTemplate.setMaxRows(maxRows);

        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        boolean truncated = false;
        List<String> warnings = new ArrayList<>();

        try {
            RowMapper<Map<String, Object>> rowMapper = (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                try {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int colCount = metaData.getColumnCount();
                    if (columns.isEmpty()) {
                        for (int i = 1; i <= colCount; i++) {
                            columns.add(metaData.getColumnLabel(i));
                        }
                    }
                    for (int i = 1; i <= colCount; i++) {
                        row.put(metaData.getColumnLabel(i), rs.getObject(i));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to map row", e);
                }
                return row;
            };

            Map<String, Object> params = (parameters != null) ? parameters : Map.of();
            if (params.isEmpty()) {
                rows = jdbcTemplate.query(sql, rowMapper);
            } else {
                rows = namedParameterJdbcTemplate.query(sql, params, rowMapper);
            }
            rowCount = rows.size();
            truncated = rowCount >= maxRows;

            if (truncated) {
                warnings.add("Results were limited to " + maxRows + " rows");
            }

            resultRedactionService.redact(rows);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("QueryExecutionService: query failed after {}ms, error={}", elapsed, e.getMessage());
            return new QueryResult(queryId, null, List.of(), List.of(), 0, false,
                elapsed, null, List.of(), List.of("Query execution failed: " + e.getMessage()), null);
        }

        long durationMs = System.currentTimeMillis() - start;

        String summary = resultSummarizer.summarize(columns, rows);
        List<com.example.platform.federation.nlq.domain.ChartSuggestion> chartSuggestions =
            chartSuggestionService.suggest(columns, rows);

        log.info("QueryExecutionService: queryId={} completed, rows={}, duration={}ms", queryId, rowCount, durationMs);

        return new QueryResult(queryId, null, columns, rows, rowCount, truncated,
            durationMs, summary, chartSuggestions, warnings, null);
    }

}
