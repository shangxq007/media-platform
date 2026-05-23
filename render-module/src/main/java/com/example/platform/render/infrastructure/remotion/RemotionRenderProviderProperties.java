package com.example.platform.render.infrastructure.remotion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.remotion")
public class RemotionRenderProviderProperties {

    private boolean enabled;
    private String cli = "npx";
    private String remotionArgs = "remotion";
    private String compositionId = "Main";
    private long timeoutMillis = 900_000;
    private boolean stubOnMissingCli = true;
    private String projectDir = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCli() {
        return cli;
    }

    public void setCli(String cli) {
        this.cli = cli;
    }

    public String getRemotionArgs() {
        return remotionArgs;
    }

    public void setRemotionArgs(String remotionArgs) {
        this.remotionArgs = remotionArgs;
    }

    public String getCompositionId() {
        return compositionId;
    }

    public void setCompositionId(String compositionId) {
        this.compositionId = compositionId;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isStubOnMissingCli() {
        return stubOnMissingCli;
    }

    public void setStubOnMissingCli(boolean stubOnMissingCli) {
        this.stubOnMissingCli = stubOnMissingCli;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }
}
