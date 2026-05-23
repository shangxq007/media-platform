package com.example.platform.workflow.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform Temporal settings (namespace naming, worker expectations, shutdown).
 */
@ConfigurationProperties(prefix = "app.temporal")
public class AppTemporalProperties {

    private boolean enabled = false;
    private int shutdownAwaitSeconds = 25;
    /** Explicit namespace; overrides {@link #namespacePrefix}-{environment}. */
    private String namespace = "";
    /** Environment suffix: dev | staging | prod (see {@code PLATFORM_ENV}). */
    private String environment = "";
    private String namespacePrefix = "media-platform";
    private String taskQueue = RenderTaskQueue.NAME;
    /** When true, logs error on startup if {@code WorkerFactory} is absent while Temporal is enabled. */
    private boolean workerRequired = true;
    /** Fail application startup when worker is required but missing (production hardening). */
    private boolean failOnMissingWorker = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getShutdownAwaitSeconds() {
        return shutdownAwaitSeconds;
    }

    public void setShutdownAwaitSeconds(int shutdownAwaitSeconds) {
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String getTaskQueue() {
        return taskQueue;
    }

    public void setTaskQueue(String taskQueue) {
        this.taskQueue = taskQueue;
    }

    public boolean isWorkerRequired() {
        return workerRequired;
    }

    public void setWorkerRequired(boolean workerRequired) {
        this.workerRequired = workerRequired;
    }

    public boolean isFailOnMissingWorker() {
        return failOnMissingWorker;
    }

    public void setFailOnMissingWorker(boolean failOnMissingWorker) {
        this.failOnMissingWorker = failOnMissingWorker;
    }

    public String resolveNamespace() {
        return TemporalNamespaceResolver.resolve(this, null, null);
    }
}
