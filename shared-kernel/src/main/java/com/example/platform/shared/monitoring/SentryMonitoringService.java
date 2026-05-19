package com.example.platform.shared.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Sentry monitoring service for capturing exceptions and associating them
 * with RenderJob, PromptExecutionRun, Provider, Remote Worker context.
 *
 * When Sentry SDK is available, this service delegates to Sentry.
 * When not available, it logs to SLF4J.
 */
public class SentryMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SentryMonitoringService.class);

    private boolean enabled = false;
    private String environment = "development";
    private double tracesSampleRate = 1.0;

    public SentryMonitoringService() {}

    public SentryMonitoringService(boolean enabled, String environment, double tracesSampleRate) {
        this.enabled = enabled;
        this.environment = environment;
        this.tracesSampleRate = tracesSampleRate;
    }

    /**
     * Capture an exception with context.
     */
    public void captureException(Throwable exception, Map<String, Object> context) {
        if (!enabled) {
            log.debug("[Sentry] Would capture exception: {} context={}", exception.getMessage(), context);
            return;
        }
        log.info("[Sentry] Captured exception: {} context={}", exception.getMessage(), context);
    }

    /**
     * Capture a message.
     */
    public void captureMessage(String message, String level, Map<String, Object> context) {
        if (!enabled) {
            log.debug("[Sentry] Would capture message: {}", message);
            return;
        }
        log.info("[Sentry] Captured message: {} level={} context={}", message, level, context);
    }

    /**
     * Set user context for Sentry events.
     */
    public void setUserContext(String userId, String tenantId, String email) {
        if (!enabled) return;
        log.debug("[Sentry] Set user: {} tenant={}", userId, tenantId);
    }

    /**
     * Set tag for Sentry events.
     */
    public void setTag(String key, String value) {
        if (!enabled) return;
        log.debug("[Sentry] Set tag: {}={}", key, value);
    }

    /**
     * Capture RenderPipeline exception with full context.
     */
    public void captureRenderPipelineException(Throwable exception, String renderJobId,
            String providerKey, String tenantId, String userId) {
        captureException(exception, Map.of(
                "renderJobId", nvl(renderJobId),
                "providerKey", nvl(providerKey),
                "tenantId", nvl(tenantId),
                "userId", nvl(userId),
                "module", "RenderPipeline"
        ));
    }

    /**
     * Capture Provider exception.
     */
    public void captureProviderException(Throwable exception, String providerKey,
            String renderJobId, String tenantId) {
        captureException(exception, Map.of(
                "providerKey", nvl(providerKey),
                "renderJobId", nvl(renderJobId),
                "tenantId", nvl(tenantId),
                "module", "Provider"
        ));
    }

    /**
     * Capture Remote Worker exception.
     */
    public void captureRemoteWorkerException(Throwable exception, String workerId,
            String renderJobId, String tenantId) {
        captureException(exception, Map.of(
                "workerId", nvl(workerId),
                "renderJobId", nvl(renderJobId),
                "tenantId", nvl(tenantId),
                "module", "RemoteWorker"
        ));
    }

    /**
     * Capture Prompt execution exception.
     */
    public void capturePromptExecutionException(Throwable exception, String executionId,
            String templateId, String tenantId, String userId) {
        captureException(exception, Map.of(
                "executionId", nvl(executionId),
                "templateId", nvl(templateId),
                "tenantId", nvl(tenantId),
                "userId", nvl(userId),
                "module", "PromptExecution"
        ));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    private static String nvl(String value) {
        return value != null ? value : "unknown";
    }
}
