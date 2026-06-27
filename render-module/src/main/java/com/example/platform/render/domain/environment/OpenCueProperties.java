package com.example.platform.render.domain.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenCue environment configuration properties.
 *
 * <p>All values have safe defaults. OpenCue is disabled by default.
 * Production dispatch requires explicit configuration. Network submit
 * is disabled unless explicitly enabled for a controlled environment.
 *
 * <p>Phase 1: Stub submit/cancel/status. No real REST/gRPC client.
 */
@Validated
@ConfigurationProperties(prefix = "opencue")
public class OpenCueProperties {

    private String server = "localhost";
    private int grpcPort = 8443;
    private int timeoutSec = 300;
    private int submitTimeoutSec = 60;
    private int statusTimeoutSec = 30;
    private int cancelTimeoutSec = 30;
    private boolean enabled = false;
    private int maxLayers = 128;
    private int maxCommandsPerLayer = 256;
    private int maxEnvironmentVariables = 64;
    private int maxTags = 32;
    private int minPriority = 1;
    private int maxPriority = 999;
    private String defaultOwner = "platform";
    private int defaultPriority = 50;
    private boolean allowNetworkSubmit = false;
    private boolean stubModeEnabled = true;
    private boolean productionSubmitEnabled = false;

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public int getSubmitTimeoutSec() {
        return submitTimeoutSec;
    }

    public void setSubmitTimeoutSec(int submitTimeoutSec) {
        this.submitTimeoutSec = submitTimeoutSec;
    }

    public int getStatusTimeoutSec() {
        return statusTimeoutSec;
    }

    public void setStatusTimeoutSec(int statusTimeoutSec) {
        this.statusTimeoutSec = statusTimeoutSec;
    }

    public int getCancelTimeoutSec() {
        return cancelTimeoutSec;
    }

    public void setCancelTimeoutSec(int cancelTimeoutSec) {
        this.cancelTimeoutSec = cancelTimeoutSec;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxLayers() {
        return maxLayers;
    }

    public void setMaxLayers(int maxLayers) {
        this.maxLayers = maxLayers;
    }

    public int getMaxCommandsPerLayer() {
        return maxCommandsPerLayer;
    }

    public void setMaxCommandsPerLayer(int maxCommandsPerLayer) {
        this.maxCommandsPerLayer = maxCommandsPerLayer;
    }

    public int getMaxEnvironmentVariables() {
        return maxEnvironmentVariables;
    }

    public void setMaxEnvironmentVariables(int maxEnvironmentVariables) {
        this.maxEnvironmentVariables = maxEnvironmentVariables;
    }

    public int getMaxTags() {
        return maxTags;
    }

    public void setMaxTags(int maxTags) {
        this.maxTags = maxTags;
    }

    public int getMinPriority() {
        return minPriority;
    }

    public void setMinPriority(int minPriority) {
        this.minPriority = minPriority;
    }

    public int getMaxPriority() {
        return maxPriority;
    }

    public void setMaxPriority(int maxPriority) {
        this.maxPriority = maxPriority;
    }

    public String getDefaultOwner() {
        return defaultOwner;
    }

    public void setDefaultOwner(String defaultOwner) {
        this.defaultOwner = defaultOwner;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public boolean isAllowNetworkSubmit() {
        return allowNetworkSubmit;
    }

    public void setAllowNetworkSubmit(boolean allowNetworkSubmit) {
        this.allowNetworkSubmit = allowNetworkSubmit;
    }

    public boolean isStubModeEnabled() {
        return stubModeEnabled;
    }

    public void setStubModeEnabled(boolean stubModeEnabled) {
        this.stubModeEnabled = stubModeEnabled;
    }

    public boolean isProductionSubmitEnabled() {
        return productionSubmitEnabled;
    }

    public void setProductionSubmitEnabled(boolean productionSubmitEnabled) {
        this.productionSubmitEnabled = productionSubmitEnabled;
    }
}
