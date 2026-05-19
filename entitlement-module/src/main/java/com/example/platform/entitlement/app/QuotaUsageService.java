package com.example.platform.entitlement.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuotaUsageService {

    private static final Logger log = LoggerFactory.getLogger(QuotaUsageService.class);

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> usage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> lastReset = new ConcurrentHashMap<>();

    public long getUsage(String subjectId, String featureCode) {
        return usage.getOrDefault(subjectId, new ConcurrentHashMap<>())
                .getOrDefault(featureCode, 0L);
    }

    public void incrementUsage(String subjectId, String featureCode, long delta) {
        usage.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>())
                .merge(featureCode, delta, Long::sum);
        log.debug("Incremented usage for {} / {} by {}", subjectId, featureCode, delta);
    }

    public void setUsage(String subjectId, String featureCode, long value) {
        usage.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>())
                .put(featureCode, value);
    }

    public void resetUsage(String subjectId, String featureCode) {
        ConcurrentHashMap<String, Long> subjectUsage = usage.get(subjectId);
        if (subjectUsage != null) {
            subjectUsage.put(featureCode, 0L);
        }
        lastReset.computeIfAbsent(subjectId, k -> new ConcurrentHashMap<>())
                .put(featureCode, Instant.now());
    }

    public void resetAll(String subjectId) {
        usage.remove(subjectId);
        lastReset.remove(subjectId);
    }

    public Map<String, Long> getAllUsage(String subjectId) {
        return Map.copyOf(usage.getOrDefault(subjectId, new ConcurrentHashMap<>()));
    }

    public Instant getLastReset(String subjectId, String featureCode) {
        return lastReset.getOrDefault(subjectId, new ConcurrentHashMap<>())
                .get(featureCode);
    }
}
