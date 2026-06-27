package com.example.platform.render.infrastructure.remotion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.remotion.worker")
public class RemotionWorkerProperties {

    private String workerImage = "platform/remotion-worker:placeholder";
    private String nodeVersion = "22";
    private String remotionVersion = "4.x";
    private String chromeMode = "headless-shell";
    private long renderTimeoutMillis = 900_000;
    private long maxOutputSizeBytes = 2_147_483_648L;
    private int maxDurationSeconds = 3_600;
    private int maxWidth = 7680;
    private int maxHeight = 4320;
    private int maxFps = 120;
    private String templateRegistryRoot = "bundled://remotion-templates/";
    private String workingDir = "/tmp/remotion-worker";
    private String outputDir = "/tmp/remotion-worker/output";
    private boolean networkAllowed = false;
    private boolean npmInstallAllowed = false;
    private boolean userCodeExecutionAllowed = false;
    private boolean systemFontsAllowed = false;
    private boolean shellCommandsAllowed = false;
    private boolean productionDispatchEnabled = false;

    public String getWorkerImage() {
        return workerImage;
    }

    public void setWorkerImage(String workerImage) {
        this.workerImage = workerImage;
    }

    public String getNodeVersion() {
        return nodeVersion;
    }

    public void setNodeVersion(String nodeVersion) {
        this.nodeVersion = nodeVersion;
    }

    public String getRemotionVersion() {
        return remotionVersion;
    }

    public void setRemotionVersion(String remotionVersion) {
        this.remotionVersion = remotionVersion;
    }

    public String getChromeMode() {
        return chromeMode;
    }

    public void setChromeMode(String chromeMode) {
        this.chromeMode = chromeMode;
    }

    public long getRenderTimeoutMillis() {
        return renderTimeoutMillis;
    }

    public void setRenderTimeoutMillis(long renderTimeoutMillis) {
        this.renderTimeoutMillis = renderTimeoutMillis;
    }

    public long getMaxOutputSizeBytes() {
        return maxOutputSizeBytes;
    }

    public void setMaxOutputSizeBytes(long maxOutputSizeBytes) {
        this.maxOutputSizeBytes = maxOutputSizeBytes;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getMaxFps() {
        return maxFps;
    }

    public void setMaxFps(int maxFps) {
        this.maxFps = maxFps;
    }

    public String getTemplateRegistryRoot() {
        return templateRegistryRoot;
    }

    public void setTemplateRegistryRoot(String templateRegistryRoot) {
        this.templateRegistryRoot = templateRegistryRoot;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public boolean isNetworkAllowed() {
        return networkAllowed;
    }

    public void setNetworkAllowed(boolean networkAllowed) {
        this.networkAllowed = networkAllowed;
    }

    public boolean isNpmInstallAllowed() {
        return npmInstallAllowed;
    }

    public void setNpmInstallAllowed(boolean npmInstallAllowed) {
        this.npmInstallAllowed = npmInstallAllowed;
    }

    public boolean isUserCodeExecutionAllowed() {
        return userCodeExecutionAllowed;
    }

    public void setUserCodeExecutionAllowed(boolean userCodeExecutionAllowed) {
        this.userCodeExecutionAllowed = userCodeExecutionAllowed;
    }

    public boolean isSystemFontsAllowed() {
        return systemFontsAllowed;
    }

    public void setSystemFontsAllowed(boolean systemFontsAllowed) {
        this.systemFontsAllowed = systemFontsAllowed;
    }

    public boolean isShellCommandsAllowed() {
        return shellCommandsAllowed;
    }

    public void setShellCommandsAllowed(boolean shellCommandsAllowed) {
        this.shellCommandsAllowed = shellCommandsAllowed;
    }

    public boolean isProductionDispatchEnabled() {
        return productionDispatchEnabled;
    }

    public void setProductionDispatchEnabled(boolean productionDispatchEnabled) {
        this.productionDispatchEnabled = productionDispatchEnabled;
    }
}
