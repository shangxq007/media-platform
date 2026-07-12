package com.example.platform.ingest.preflight.policy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Report-only ingest preflight policy configuration.
 * Disabled by default. No enforcement. No persistence.
 */
@ConfigurationProperties(prefix = "ingest.preflight.policy.report-only")
public class IngestPreflightPolicyProperties {

    private boolean enabled = false;
    private String mode = "report_only";
    private String profile = "preview_safe";
    private boolean failOpen = true;
    private boolean includeWarningFindings = true;
    private boolean includeMediaTechnicalFindings = true;
    private boolean includeRejectCandidates = true;
    private int maxFindings = 50;
    private boolean logResult = true;

    // Getters/setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    public boolean isIncludeWarningFindings() { return includeWarningFindings; }
    public void setIncludeWarningFindings(boolean includeWarningFindings) { this.includeWarningFindings = includeWarningFindings; }
    public boolean isIncludeMediaTechnicalFindings() { return includeMediaTechnicalFindings; }
    public void setIncludeMediaTechnicalFindings(boolean includeMediaTechnicalFindings) { this.includeMediaTechnicalFindings = includeMediaTechnicalFindings; }
    public boolean isIncludeRejectCandidates() { return includeRejectCandidates; }
    public void setIncludeRejectCandidates(boolean includeRejectCandidates) { this.includeRejectCandidates = includeRejectCandidates; }
    public int getMaxFindings() { return maxFindings; }
    public void setMaxFindings(int maxFindings) { this.maxFindings = maxFindings; }
    public boolean isLogResult() { return logResult; }
    public void setLogResult(boolean logResult) { this.logResult = logResult; }
}
