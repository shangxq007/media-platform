package com.example.platform.extension.api;

import com.example.platform.extension.app.CliToolInvocationService;
import com.example.platform.extension.app.ExtensionCatalogService;
import com.example.platform.extension.app.ToolRunner;
import com.example.platform.extension.domain.ToolRunRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/extensions")
public class ExtensionController {

    private final ExtensionCatalogService extensionCatalogService;
    private final ToolRunner toolRunner;
    private final CliToolInvocationService cliToolInvocationService;

    public ExtensionController(
            ExtensionCatalogService extensionCatalogService,
            ToolRunner toolRunner,
            CliToolInvocationService cliToolInvocationService) {
        this.extensionCatalogService = extensionCatalogService;
        this.toolRunner = toolRunner;
        this.cliToolInvocationService = cliToolInvocationService;
    }

    @GetMapping
    public List<String> list() {
        return extensionCatalogService.extensionCodes();
    }

    @PostMapping("/tool-run")
    public Map<String, Object> runTool(@RequestBody ToolRunRequest request) {
        var result = toolRunner.run(request);
        return Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    }

    /** Config-driven tools (allowlist + templates); prefer this over raw {@code /tool-run} for production. */
    @GetMapping("/cli-tools")
    public Map<String, List<String>> listCliTools() {
        return Map.of("tools", cliToolInvocationService.listToolKeys());
    }

    @PostMapping("/cli-tools/{toolKey}/run")
    public Map<String, Object> runCliTool(
            @PathVariable String toolKey, @RequestBody(required = false) CliToolRunBody body) {
        Map<String, String> params = body != null && body.params() != null ? body.params() : Map.of();
        var result = cliToolInvocationService.run(toolKey, params);
        return Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    }
}
