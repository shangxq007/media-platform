package com.example.platform.extension.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative CLI tools: logical executables (allowlist paths) + named recipes (arg templates, timeouts).
 * Same shape can later be hydrated from a config server or DB without changing the execution path.
 */
@ConfigurationProperties(prefix = "app.cli-tools")
public class CliToolsProperties {

    /**
     * Logical name → absolute path on the host or container (this map is the allowlist).
     */
    private Map<String, String> executables = new LinkedHashMap<>();

    /**
     * Tool key → how to invoke a registered executable with templated args.
     */
    private Map<String, Recipe> tools = new LinkedHashMap<>();

    public Map<String, String> getExecutables() {
        return executables;
    }

    public void setExecutables(Map<String, String> executables) {
        this.executables = executables != null ? executables : new LinkedHashMap<>();
    }

    public Map<String, Recipe> getTools() {
        return tools;
    }

    public void setTools(Map<String, Recipe> tools) {
        this.tools = tools != null ? tools : new LinkedHashMap<>();
    }

    public static class Recipe {

        /** Key into {@link #executables}. */
        private String executableKey;

        /** Arg tokens; use {@code {name}} placeholders filled from per-run params. */
        private List<String> args = new ArrayList<>();

        private long timeoutMillis = 60_000L;

        public String getExecutableKey() {
            return executableKey;
        }

        public void setExecutableKey(String executableKey) {
            this.executableKey = executableKey;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args != null ? args : new ArrayList<>();
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
    }
}
