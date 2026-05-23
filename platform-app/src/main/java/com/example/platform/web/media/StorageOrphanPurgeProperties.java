package com.example.platform.web.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controlled purge of bucket orphan objects (AST-005). Requires {@link #approvalToken} on every purge call.
 */
@Component
@ConfigurationProperties(prefix = "platform.storage.orphan-purge")
public class StorageOrphanPurgeProperties {

    /** When false, purge API always rejects (scan-only mode). */
    private boolean enabled = false;

    /**
     * Shared secret for {@code POST /storage-orphans/purge}. Empty means purge is disabled even if {@link #enabled} is true.
     */
    private String approvalToken = "";

    private int maxDeletesPerRun = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApprovalToken() {
        return approvalToken;
    }

    public void setApprovalToken(String approvalToken) {
        this.approvalToken = approvalToken;
    }

    public int getMaxDeletesPerRun() {
        return maxDeletesPerRun;
    }

    public void setMaxDeletesPerRun(int maxDeletesPerRun) {
        this.maxDeletesPerRun = maxDeletesPerRun;
    }

    public boolean isPurgeAllowed() {
        return enabled && approvalToken != null && !approvalToken.isBlank();
    }
}
