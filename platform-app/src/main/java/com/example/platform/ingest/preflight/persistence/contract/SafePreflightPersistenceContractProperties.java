package com.example.platform.ingest.preflight.persistence.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingest.preflight.safe-report.persistence")
public class SafePreflightPersistenceContractProperties {

    private SafePreflightPersistenceMode mode = SafePreflightPersistenceMode.DISABLED;
    private SafePreflightPersistenceAccessScope accessScope = SafePreflightPersistenceAccessScope.DEV_ONLY;
    private int retentionDays = 7;
    private boolean failOpen = true;
    private boolean publicResponseEnabled = false;
    private boolean allowRawMetadata = false;
    private boolean allowLocalPath = false;
    private boolean allowStorageInternals = false;
    private boolean allowSignedUrl = false;
    private boolean allowCredentials = false;

    // Getters/setters
    public SafePreflightPersistenceMode getMode() { return mode; }
    public void setMode(SafePreflightPersistenceMode mode) { this.mode = mode; }
    public SafePreflightPersistenceAccessScope getAccessScope() { return accessScope; }
    public void setAccessScope(SafePreflightPersistenceAccessScope accessScope) { this.accessScope = accessScope; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    public boolean isPublicResponseEnabled() { return publicResponseEnabled; }
    public void setPublicResponseEnabled(boolean publicResponseEnabled) { this.publicResponseEnabled = publicResponseEnabled; }
    public boolean isAllowRawMetadata() { return allowRawMetadata; }
    public void setAllowRawMetadata(boolean allowRawMetadata) { this.allowRawMetadata = allowRawMetadata; }
    public boolean isAllowLocalPath() { return allowLocalPath; }
    public void setAllowLocalPath(boolean allowLocalPath) { this.allowLocalPath = allowLocalPath; }
    public boolean isAllowStorageInternals() { return allowStorageInternals; }
    public void setAllowStorageInternals(boolean allowStorageInternals) { this.allowStorageInternals = allowStorageInternals; }
    public boolean isAllowSignedUrl() { return allowSignedUrl; }
    public void setAllowSignedUrl(boolean allowSignedUrl) { this.allowSignedUrl = allowSignedUrl; }
    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
}
