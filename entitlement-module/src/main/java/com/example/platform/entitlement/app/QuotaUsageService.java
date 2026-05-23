package com.example.platform.entitlement.app;

import com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuotaUsageService {

    private static final Logger log = LoggerFactory.getLogger(QuotaUsageService.class);

    private final Optional<QuotaUsageJdbcRepository> jdbcRepository;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> usage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> lastReset = new ConcurrentHashMap<>();

    public QuotaUsageService(Optional<QuotaUsageJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void hydrateUsage(String subjectId, String featureCode, long value) {
        usage.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>()).put(featureCode, value);
    }

    public long getUsage(String subjectId, String featureCode) {
        Long cached = usage.getOrDefault(subjectId, new ConcurrentHashMap<>()).get(featureCode);
        if (cached != null) {
            return cached;
        }
        if (jdbcRepository.isPresent()) {
            long fromDb = jdbcRepository.get().getUsage(subjectId, featureCode);
            hydrateUsage(subjectId, featureCode, fromDb);
            return fromDb;
        }
        return 0L;
    }

    public void incrementUsage(String subjectId, String featureCode, long delta) {
        long next = getUsage(subjectId, featureCode) + delta;
        setUsage(subjectId, featureCode, next);
        log.debug("Incremented usage for {} / {} by {} → {}", subjectId, featureCode, delta, next);
    }

    public void setUsage(String subjectId, String featureCode, long value) {
        usage.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>()).put(featureCode, value);
        jdbcRepository.ifPresent(r -> r.setUsage(subjectId, featureCode, value));
    }

    public void resetUsage(String subjectId, String featureCode) {
        setUsage(subjectId, featureCode, 0L);
        lastReset.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>())
                .put(featureCode, Instant.now());
    }

    public void resetAll(String subjectId) {
        usage.remove(subjectId);
        lastReset.remove(subjectId);
        jdbcRepository.ifPresent(r -> {
            for (Map.Entry<String, String> key : r.loadSubjectFeatures()) {
                if (key.getKey().equals(subjectId)) {
                    r.setUsage(subjectId, key.getValue(), 0L);
                }
            }
        });
    }

    public Map<String, Long> getAllUsage(String subjectId) {
        return Map.copyOf(usage.getOrDefault(subjectId, new ConcurrentHashMap<>()));
    }

    public Instant getLastReset(String subjectId, String featureCode) {
        return lastReset.getOrDefault(subjectId, new ConcurrentHashMap<>()).get(featureCode);
    }
}
