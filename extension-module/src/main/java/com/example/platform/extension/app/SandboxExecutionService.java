package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class SandboxExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutionService.class);

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final long MAX_TIMEOUT_MS = 120_000;
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private final AuditPort auditPort;
    private final ExtensionResourceLimiter resourceLimiter;
    private final ExtensionAuditService auditService;
    private final ExecutorService executorService;

    public SandboxExecutionService(AuditPort auditPort,
                                   ExtensionResourceLimiter resourceLimiter,
                                   ExtensionAuditService auditService) {
        this.auditPort = auditPort;
        this.resourceLimiter = resourceLimiter;
        this.auditService = auditService;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "extension-sandbox");
            t.setDaemon(true);
            return t;
        });
    }

    public ExtensionResult executeExtension(ExtensionContext context, String inputJson,
                                              ExtensionResourceLimits limits)
            throws ExtensionExecutionException {
        String extensionKey = context.extensionKey();
        long timeoutMs = Math.min(limits.timeoutMs(), MAX_TIMEOUT_MS);
        int maxOutputBytes = (int) Math.min(limits.maxOutputBytes(), DEFAULT_MAX_OUTPUT_BYTES);
        OffsetDateTime startedAt = OffsetDateTime.now();

        ExtensionResourceLimiter.ExtensionCheckResult checkResult =
                resourceLimiter.checkAndAcquire(extensionKey, inputJson != null ? inputJson.getBytes().length : 0);
        if (!checkResult.allowed()) {
            auditService.recordSecurityViolation(extensionKey, context.userId(),
                    "Resource limit: " + checkResult.rejectionCode());
            return ExtensionResult.failure(checkResult.rejectionCode(), checkResult.rejectionReason());
        }

        auditService.recordExecutionStart(extensionKey, context.extensionVersion(),
                context.tenantId(), context.userId(), context.traceId(),
                context.trustLevel() != null ? context.trustLevel().name() : null);

        try {
            Future<ExtensionResult> future = executorService.submit(() -> {
                String output = invokeExtensionSpi(context, inputJson);
                long elapsed = java.time.Duration.between(startedAt, OffsetDateTime.now()).toMillis();
                return ExtensionResult.success(output != null ? output : "{}", Map.of(
                        "durationMs", elapsed,
                        "extensionKey", extensionKey));
            });

            ExtensionResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (result.outputJson() != null && result.outputJson().getBytes().length > maxOutputBytes) {
                result = new ExtensionResult(
                        true,
                        result.outputJson().substring(0, maxOutputBytes) + "\n[TRUNCATED]",
                        null, null,
                        result.metrics());
            }

            long duration = java.time.Duration.between(startedAt, OffsetDateTime.now()).toMillis();
            resourceLimiter.recordOutput(extensionKey,
                    result.outputJson() != null ? result.outputJson().getBytes().length : 0);

            ExtensionResult finalResult = result.withMetric("durationMs", duration);
            auditService.recordExecutionComplete(extensionKey, context.extensionVersion(),
                    context.tenantId(), context.userId(), context.traceId(),
                    context.trustLevel() != null ? context.trustLevel().name() : null,
                    duration, finalResult.outputJson() != null ? finalResult.outputJson().length() : 0);

            return finalResult;

        } catch (TimeoutException e) {
            log.warn("Extension {} timed out after {}ms", extensionKey, timeoutMs);
            auditService.recordExecutionTimeout(extensionKey, context.extensionVersion(),
                    context.tenantId(), context.userId(), context.traceId(), timeoutMs);
            return ExtensionResult.failure("EXT-408",
                    "Extension execution timed out after " + timeoutMs + "ms",
                    Map.of("timeoutMs", timeoutMs));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auditService.recordExecutionFailed(extensionKey, context.extensionVersion(),
                    context.tenantId(), context.userId(), context.traceId(),
                    "EXT-500", "Interrupted");
            return ExtensionResult.failure("EXT-500", "Extension execution interrupted");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            auditService.recordExecutionFailed(extensionKey, context.extensionVersion(),
                    context.tenantId(), context.userId(), context.traceId(),
                    "EXT-500", errorMsg);
            return ExtensionResult.failure("EXT-500",
                    "Extension execution failed: " + errorMsg);

        } catch (Exception e) {
            auditService.recordExecutionFailed(extensionKey, context.extensionVersion(),
                    context.tenantId(), context.userId(), context.traceId(),
                    "EXT-500", e.getMessage());
            return ExtensionResult.failure("EXT-500", "Unexpected error: " + e.getMessage());

        } finally {
            resourceLimiter.release(checkResult);
        }
    }

    public String executeInSandbox(String extensionKey, String inputJson, String tenantId,
                                    String userId, Object extensionHolder)
            throws ExtensionExecutionException {
        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(extensionKey)
                .tenantId(tenantId)
                .userId(userId)
                .traceId(Ids.newId("trace"))
                .trustLevel(ExtensionTrustLevel.SEMI_TRUSTED)
                .build();
        ExtensionResult result = executeExtension(context, inputJson,
                ExtensionResourceLimits.DEFAULTS);
        if (!result.success()) {
            throw new ExtensionExecutionException(extensionKey, result.errorCode(), result.errorMessage());
        }
        return result.outputJson();
    }

    private String invokeExtensionSpi(ExtensionContext context, String inputJson) {
        return "{\"status\":\"executed\",\"extensionKey\":\"" + context.extensionKey() + "\"}";
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
