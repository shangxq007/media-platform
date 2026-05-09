package com.example.platform.extension.app;

import com.example.platform.extension.config.CliToolsProperties;
import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CliToolInvocationServiceTest {

    private CliToolInvocationService service;
    private CliToolsProperties properties;
    private CliTemplateResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new CliToolsProperties();
        resolver = new CliTemplateResolver();
        ToolRunner noopRunner = request -> new ToolRunResult(0, "ok", "");
        service = new CliToolInvocationService(properties, resolver, noopRunner);
    }

    @Test
    void listToolKeysReturnsEmptyWhenNoToolsConfigured() {
        assertTrue(service.listToolKeys().isEmpty());
    }

    @Test
    void listToolKeysReturnsSortedKeys() {
        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe zRecipe = new CliToolsProperties.Recipe();
        zRecipe.setExecutableKey("echo");
        tools.put("z-tool", zRecipe);
        CliToolsProperties.Recipe aRecipe = new CliToolsProperties.Recipe();
        aRecipe.setExecutableKey("echo");
        tools.put("a-tool", aRecipe);
        CliToolsProperties.Recipe mRecipe = new CliToolsProperties.Recipe();
        mRecipe.setExecutableKey("echo");
        tools.put("m-tool", mRecipe);
        properties.setTools(tools);

        List<String> keys = service.listToolKeys();
        assertEquals(List.of("a-tool", "m-tool", "z-tool"), keys);
    }

    @Test
    void runThrowsNotFoundForUnknownToolKey() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.run("nonexistent", Map.of()));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void runThrowsServiceUnavailableWhenExecutableNotConfigured() {
        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe recipe = new CliToolsProperties.Recipe();
        recipe.setExecutableKey("missing-exec");
        tools.put("my-tool", recipe);
        properties.setTools(tools);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.run("my-tool", Map.of()));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    void runThrowsIllegalStateExceptionWhenRecipeHasNoExecutableKey() {
        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe recipe = new CliToolsProperties.Recipe();
        recipe.setExecutableKey("");
        tools.put("bad-tool", recipe);
        properties.setTools(tools);

        assertThrows(
                IllegalStateException.class,
                () -> service.run("bad-tool", Map.of()));
    }

    @Test
    void runThrowsBadRequestForMissingTemplateParams() {
        Map<String, String> executables = new LinkedHashMap<>();
        executables.put("echo", "/bin/echo");
        properties.setExecutables(executables);

        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe recipe = new CliToolsProperties.Recipe();
        recipe.setExecutableKey("echo");
        recipe.setArgs(List.of("{input}"));
        tools.put("echo-tool", recipe);
        properties.setTools(tools);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.run("echo-tool", Map.of()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void runDelegatesToToolRunnerWithResolvedArgs() {
        Map<String, String> executables = new LinkedHashMap<>();
        executables.put("echo", "/bin/echo");
        properties.setExecutables(executables);

        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe recipe = new CliToolsProperties.Recipe();
        recipe.setExecutableKey("echo");
        recipe.setArgs(List.of("-n", "{msg}"));
        recipe.setTimeoutMillis(5000L);
        tools.put("echo-tool", recipe);
        properties.setTools(tools);

        ToolRunner capturingRunner = request -> {
            assertEquals("/bin/echo", request.executable());
            assertEquals(List.of("-n", "hello"), request.args());
            assertEquals(5000L, request.timeoutMillis());
            return new ToolRunResult(0, "captured", "");
        };
        service = new CliToolInvocationService(properties, resolver, capturingRunner);

        ToolRunResult result = service.run("echo-tool", Map.of("msg", "hello"));
        assertEquals(0, result.exitCode());
        assertEquals("captured", result.stdout());
    }

    @Test
    void runPassesNullParamsAsEmptyMap() {
        Map<String, String> executables = new LinkedHashMap<>();
        executables.put("echo", "/bin/echo");
        properties.setExecutables(executables);

        Map<String, CliToolsProperties.Recipe> tools = new LinkedHashMap<>();
        CliToolsProperties.Recipe recipe = new CliToolsProperties.Recipe();
        recipe.setExecutableKey("echo");
        recipe.setArgs(List.of("static-arg"));
        tools.put("static-tool", recipe);
        properties.setTools(tools);

        ToolRunner noopRunner = request -> new ToolRunResult(0, "ok", "");
        service = new CliToolInvocationService(properties, resolver, noopRunner);

        ToolRunResult result = service.run("static-tool", null);
        assertEquals(0, result.exitCode());
    }
}
