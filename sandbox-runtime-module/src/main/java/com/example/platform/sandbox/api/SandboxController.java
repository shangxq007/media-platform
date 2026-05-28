package com.example.platform.sandbox.api;

import com.example.platform.sandbox.app.SandboxRuntimeService;
import com.example.platform.sandbox.domain.DefaultSandboxSecurityPolicy;
import com.example.platform.sandbox.domain.SandboxExecutor.SandboxResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxController {

    private final SandboxRuntimeService service;
    private final DefaultSandboxSecurityPolicy securityPolicy;

    public SandboxController(SandboxRuntimeService service,
                              DefaultSandboxSecurityPolicy securityPolicy) {
        this.service = service;
        this.securityPolicy = securityPolicy;
    }

    @GetMapping("/runtime/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody SandboxExecuteRequest request) {
        if (!service.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Sandbox module is disabled",
                    "message", "Code execution is not available in this environment. " +
                               "Enable sandbox.enabled=true for dev/test only."));
        }

        if (request.code() == null || request.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code must not be blank"));
        }

        // Security policy check only applies to in-process mode
        if (service.getExecutionMode() == com.example.platform.sandbox.app.SandboxExecutionMode.IN_PROCESS) {
            if (!securityPolicy.isCodeSafe(request.code())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Code contains blocked patterns",
                        "message", "Potentially dangerous code detected by security policy"));
            }
        }

        SandboxResult result = service.execute(
                request.code(), request.language(), request.timeoutMs());

        if (result.exitCode() == 0) {
            return ResponseEntity.ok(Map.of(
                    "exitCode", result.exitCode(),
                    "output", result.output()));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "exitCode", result.exitCode(),
                    "output", result.output(),
                    "error", result.error()));
        }
    }

    record SandboxExecuteRequest(String code, String language, long timeoutMs) {
        SandboxExecuteRequest {
            language = language != null ? language : "javascript";
            timeoutMs = timeoutMs > 0 ? timeoutMs : 30_000;
        }
    }
}
