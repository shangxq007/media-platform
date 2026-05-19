package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Service
public class ExtensionResourceLimiter {

    private static final Logger log = LoggerFactory.getLogger(ExtensionResourceLimiter.class);

    private final AuditPort auditPort;
    private final ConcurrentHashMap<String, Semaphore> concurrencySemaphores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> queueSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> inputByteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> outputByteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExtensionResourceLimits> limits = new ConcurrentHashMap<>();

    public ExtensionResourceLimiter(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void registerLimits(String extensionCode, ExtensionResourceLimits resourceLimits) {
        limits.put(extensionCode, resourceLimits);
        concurrencySemaphores.put(extensionCode, new Semaphore(resourceLimits.maxConcurrency()));
        queueSizes.put(extensionCode, new LongAdder());
        inputByteCounters.put(extensionCode, new AtomicLong(0));
        outputByteCounters.put(extensionCode, new AtomicLong(0));
    }

    public void updateLimits(String extensionCode, ExtensionResourceLimits newLimits, String updatedBy) {
        ExtensionResourceLimits old = limits.put(extensionCode, newLimits);
        concurrencySemaphores.put(extensionCode, new Semaphore(newLimits.maxConcurrency()));
        auditPort.record(updatedBy, "RESOURCE_LIMIT_UPDATED", "EXTENSION_RESOURCE",
                "resource_limit", extensionCode, Map.of(
                        "oldMaxConcurrency", old != null ? old.maxConcurrency() : 0,
                        "newMaxConcurrency", newLimits.maxConcurrency(),
                        "newMaxMemoryMb", newLimits.maxMemoryMb(),
                        "newMaxCpuPercent", newLimits.maxCpuPercent()));
    }

    public ExtensionCheckResult checkAndAcquire(String extensionCode, long inputSize) {
        ExtensionResourceLimits limit = limits.getOrDefault(extensionCode, ExtensionResourceLimits.DEFAULTS);

        if (inputSize > limit.maxInputBytes()) {
            auditPort.record("system", "RESOURCE_LIMIT_EXCEEDED", "EXTENSION_RESOURCE",
                    "resource_limit", extensionCode, Map.of(
                            "limitType", "INPUT_SIZE",
                            "requested", inputSize,
                            "maxAllowed", limit.maxInputBytes()));
            return ExtensionCheckResult.rejected("INPUT_TOO_LARGE",
                    "Input size " + inputSize + " exceeds max " + limit.maxInputBytes() + " bytes");
        }

        LongAdder queueSize = queueSizes.computeIfAbsent(extensionCode, k -> new LongAdder());
        if (queueSize.sum() >= limit.maxQueueSize()) {
            auditPort.record("system", "RESOURCE_LIMIT_EXCEEDED", "EXTENSION_RESOURCE",
                    "resource_limit", extensionCode, Map.of(
                            "limitType", "QUEUE_FULL",
                            "queueSize", queueSize.sum(),
                            "maxQueueSize", limit.maxQueueSize()));
            return ExtensionCheckResult.rejected("QUEUE_FULL",
                    "Queue size " + queueSize.sum() + " exceeds max " + limit.maxQueueSize());
        }

        Semaphore semaphore = concurrencySemaphore(extensionCode, limit.maxConcurrency());
        if (!semaphore.tryAcquire()) {
            auditPort.record("system", "RESOURCE_LIMIT_EXCEEDED", "EXTENSION_RESOURCE",
                    "resource_limit", extensionCode, Map.of(
                            "limitType", "CONCURRENCY",
                            "maxConcurrency", limit.maxConcurrency()));
            return ExtensionCheckResult.rejected("CONCURRENCY_LIMIT",
                    "Max concurrency " + limit.maxConcurrency() + " reached for " + extensionCode);
        }

        queueSize.increment();
        inputByteCounters.computeIfAbsent(extensionCode, k -> new AtomicLong(0)).addAndGet(inputSize);

        return ExtensionCheckResult.acquired(semaphore, queueSize);
    }

    public void release(ExtensionCheckResult acquired) {
        if (acquired.semaphore() != null) {
            acquired.semaphore().release();
        }
        if (acquired.queueSize() != null) {
            acquired.queueSize().decrement();
        }
    }

    public void recordOutput(String extensionCode, long outputSize) {
        AtomicLong counter = outputByteCounters.computeIfAbsent(extensionCode, k -> new AtomicLong(0));
        counter.addAndGet(outputSize);
        ExtensionResourceLimits limit = limits.getOrDefault(extensionCode, ExtensionResourceLimits.DEFAULTS);
        if (outputSize > limit.maxOutputBytes()) {
            log.warn("Extension {} output {} exceeds limit {}", extensionCode, outputSize, limit.maxOutputBytes());
        }
    }

    public ExtensionResourceLimits getLimits(String extensionCode) {
        return limits.getOrDefault(extensionCode, ExtensionResourceLimits.DEFAULTS);
    }

    public Map<String, Object> getUsageStats(String extensionCode) {
        ExtensionResourceLimits limit = limits.getOrDefault(extensionCode, ExtensionResourceLimits.DEFAULTS);
        return Map.of(
                "maxConcurrency", limit.maxConcurrency(),
                "maxMemoryMb", limit.maxMemoryMb(),
                "maxCpuPercent", limit.maxCpuPercent(),
                "maxQueueSize", limit.maxQueueSize(),
                "maxInputBytes", limit.maxInputBytes(),
                "maxOutputBytes", limit.maxOutputBytes(),
                "timeoutMs", limit.timeoutMs(),
                "currentQueueSize", queueSizes.getOrDefault(extensionCode, new LongAdder()).sum(),
                "totalInputBytes", inputByteCounters.getOrDefault(extensionCode, new AtomicLong(0)).get(),
                "totalOutputBytes", outputByteCounters.getOrDefault(extensionCode, new AtomicLong(0)).get()
        );
    }

    private Semaphore concurrencySemaphore(String extensionCode, int maxConcurrency) {
        return concurrencySemaphores.computeIfAbsent(extensionCode, k -> new Semaphore(maxConcurrency));
    }

    public record ExtensionCheckResult(boolean allowed, String rejectionCode, String rejectionReason,
                                        Semaphore semaphore, LongAdder queueSize) {
        static ExtensionCheckResult acquired(Semaphore semaphore, LongAdder queueSize) {
            return new ExtensionCheckResult(true, null, null, semaphore, queueSize);
        }

        static ExtensionCheckResult rejected(String code, String reason) {
            return new ExtensionCheckResult(false, code, reason, null, null);
        }
    }
}
