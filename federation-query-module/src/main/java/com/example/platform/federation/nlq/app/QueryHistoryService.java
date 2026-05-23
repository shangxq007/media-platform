package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryHistoryRecord;
import com.example.platform.federation.nlq.infrastructure.NlqJdbcRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);

    private final Map<String, QueryHistoryRecord> historyStore = new ConcurrentHashMap<>();
    private final Optional<NlqJdbcRepository> jdbcRepository;

    public QueryHistoryService() {
        this(Optional.empty());
    }

    @Autowired
    public QueryHistoryService(Optional<NlqJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository != null ? jdbcRepository : Optional.empty();
    }

    public void hydrateRecord(QueryHistoryRecord record) {
        historyStore.put(record.queryId(), record);
    }

    public QueryHistoryRecord record(String userId, String tenantId, String workspaceId,
            String questionRedacted, String sql, List<String> datasets,
            int rowCount, long durationMs, String riskLevel, String status, String errorCode) {
        String queryId = Ids.newId("qry");
        String sqlHash = hashSql(sql);

        QueryHistoryRecord record = new QueryHistoryRecord(
            queryId, userId, tenantId, workspaceId, questionRedacted, sqlHash,
            datasets != null ? datasets : List.of(), rowCount, durationMs, riskLevel,
            status, errorCode, Instant.now()
        );

        historyStore.put(queryId, record);
        jdbcRepository.ifPresent(r -> r.saveQueryHistory(record));
        log.info("QueryHistoryService: recorded queryId={}, userId={}, status={}", queryId, userId, status);
        return record;
    }

    public Optional<QueryHistoryRecord> getById(String queryId) {
        return Optional.ofNullable(historyStore.get(queryId));
    }

    public List<QueryHistoryRecord> listByTenant(String tenantId) {
        return historyStore.values().stream()
            .filter(r -> tenantId.equals(r.tenantId()))
            .sorted(Comparator.comparing(QueryHistoryRecord::createdAt).reversed())
            .collect(Collectors.toList());
    }

    public List<QueryHistoryRecord> listByUser(String userId) {
        return historyStore.values().stream()
            .filter(r -> userId.equals(r.userId()))
            .sorted(Comparator.comparing(QueryHistoryRecord::createdAt).reversed())
            .collect(Collectors.toList());
    }

    public List<QueryHistoryRecord> listByWorkspace(String workspaceId) {
        return historyStore.values().stream()
            .filter(r -> workspaceId.equals(r.workspaceId()))
            .sorted(Comparator.comparing(QueryHistoryRecord::createdAt).reversed())
            .collect(Collectors.toList());
    }

    public List<QueryHistoryRecord> listRecent(int limit) {
        return historyStore.values().stream()
            .sorted(Comparator.comparing(QueryHistoryRecord::createdAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    private String hashSql(String sql) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sql.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return "h_" + sql.hashCode();
        }
    }
}
