package com.example.platform.extension.api.port;
import com.example.platform.extension.domain.*;
import java.util.*;
/** Canonical configured-tool registration and read contract; all path validation stays in Extension. */
public interface ToolCatalog {
    void registerExecutable(String key, String path);
    void registerTool(ToolDefinition definition);
    Optional<ToolDefinition> findTool(String key);
    List<ToolDefinition> listTools();
    String resolveExecutable(String toolKey);
    boolean isAllowedExecutable(String path);
    ToolEnvironmentReport validateEnvironment();
}
