package com.example.platform.extension.app;

import com.example.platform.extension.config.CliToolsProperties;
import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CliToolInvocationService {

    private final CliToolsProperties cliToolsProperties;
    private final CliTemplateResolver cliTemplateResolver;
    private final ToolRunner toolRunner;

    public CliToolInvocationService(
            CliToolsProperties cliToolsProperties,
            CliTemplateResolver cliTemplateResolver,
            ToolRunner toolRunner) {
        this.cliToolsProperties = cliToolsProperties;
        this.cliTemplateResolver = cliTemplateResolver;
        this.toolRunner = toolRunner;
    }

    public List<String> listToolKeys() {
        return cliToolsProperties.getTools().keySet().stream().sorted().collect(Collectors.toList());
    }

    public ToolRunResult run(String toolKey, Map<String, String> params) {
        CliToolsProperties.Recipe recipe = cliToolsProperties.getTools().get(toolKey);
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown cli tool: " + toolKey);
        }
        if (!StringUtils.hasText(recipe.getExecutableKey())) {
            throw new IllegalStateException("Tool '" + toolKey + "' has no executable-key");
        }
        String executablePath = cliToolsProperties.getExecutables().get(recipe.getExecutableKey());
        if (!StringUtils.hasText(executablePath)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Executable not configured for key: " + recipe.getExecutableKey());
        }

        List<String> args;
        try {
            args = cliTemplateResolver.resolveArgs(recipe.getArgs(), params != null ? params : Map.of());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return toolRunner.run(new ToolRunRequest(executablePath.trim(), args, recipe.getTimeoutMillis()));
    }
}
