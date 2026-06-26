package com.example.platform.render.domain.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "opencue")
public class OpenCueProperties {
    private String server = "localhost";
    private int grpcPort = 8443;
    private int timeoutSec = 300;
    private boolean enabled = false;

    public String server() { return server; }
    public void setServer(String s) { this.server = s; }
    public int timeoutSec() { return timeoutSec; }
    public void setTimeoutSec(int t) { this.timeoutSec = t; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
}
