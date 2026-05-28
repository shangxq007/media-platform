package com.example.platform.sandbox.worker.api;

import com.example.platform.sandbox.worker.app.SandboxExecutionResult;
import com.example.platform.sandbox.worker.app.SandboxExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/sandbox")
public class SandboxWorkerController {

    private static final Logger log = LoggerFactory.getLogger(SandboxWorkerController.class);

    private final SandboxExecutionService executionService;

    public SandboxWorkerController(SandboxExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody SandboxExecuteRequest request) {
        String language = request.language();
        String code = request.code();
        long timeoutMs = request.timeoutMs();

        log.info("SandboxWorkerController: execute request language={} codeLength={} timeoutMs={}",
                language, code != null ? code.length() : 0, timeoutMs);

        SandboxExecutionResult result = executionService.execute(language, code, timeoutMs);

        SandboxExecuteResponse response = new SandboxExecuteResponse(
                result.status().name(),
                result.stdout(),
                result.stderr(),
                result.exitCode(),
                result.durationMs(),
                result.truncated(),
                result.errorCode(),
                result.message(),
                "sandbox-worker-1",
                "python:3.12"
        );

        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(response);
            case TIMEOUT -> ResponseEntity.status(504).body(response);
            case DENIED -> ResponseEntity.status(403).body(response);
            case FAILED -> ResponseEntity.badRequest().body(response);
            case ERROR -> ResponseEntity.status(500).body(response);
        };
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "sandbox-worker"
        ));
    }

    record SandboxExecuteRequest(
            String language,
            String code,
            long timeoutMs,
            Map<String, String> metadata
    ) {
        SandboxExecuteRequest {
            language = language != null ? language : "";
            code = code != null ? code : "";
            timeoutMs = timeoutMs > 0 ? timeoutMs : 5_000;
            metadata = metadata != null ? metadata : Map.of();
        }
    }

    record SandboxExecuteResponse(
            String status,
            String stdout,
            String stderr,
            int exitCode,
            long durationMs,
            boolean truncated,
            String errorCode,
            String message,
            String workerId,
            String runtime
    ) {}
}
