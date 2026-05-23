package com.example.platform.render.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.timeline.asset.gc")
public class TimelineAssetGcProperties {

    private boolean enabled = true;
    private int retentionDays = 7;
    private String scheduleInterval = "PT6H";
    private boolean deleteBlobOnPurge = true;
    private int maxProjectsPerRun = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getScheduleInterval() {
        return scheduleInterval;
    }

    public void setScheduleInterval(String scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }

    public boolean isDeleteBlobOnPurge() {
        return deleteBlobOnPurge;
    }

    public void setDeleteBlobOnPurge(boolean deleteBlobOnPurge) {
        this.deleteBlobOnPurge = deleteBlobOnPurge;
    }

    public int getMaxProjectsPerRun() {
        return maxProjectsPerRun;
    }

    public void setMaxProjectsPerRun(int maxProjectsPerRun) {
        this.maxProjectsPerRun = maxProjectsPerRun;
    }
}
