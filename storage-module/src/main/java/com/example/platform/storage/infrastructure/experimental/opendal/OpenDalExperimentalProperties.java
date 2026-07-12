package com.example.platform.storage.infrastructure.experimental.opendal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenDAL experimental provider configuration.
 * Disabled by default. Not for production use.
 */
@ConfigurationProperties(prefix = "storage.experimental.opendal")
public class OpenDalExperimentalProperties {
    private boolean enabled = false;
    private String backend = "fs";
    private String root = System.getProperty("java.io.tmpdir") + "/media-platform-opendal-lab";
    private String mode = "poc";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
