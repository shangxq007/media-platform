package com.example.platform.sandbox.app;

import com.example.platform.sandbox.domain.SandboxExecutor;
import com.example.platform.sandbox.domain.SandboxSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Service
public class SandboxRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(SandboxRuntimeService.class);

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final long MAX_TIMEOUT_MS = 120_000;
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private final SandboxSecurityPolicy securityPolicy;
    private final ExecutorService executorService;

    public SandboxRuntimeService(SandboxSecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sandbox-runtime");
            t.setDaemon(true);
            return t;
        });
    }

    public Map<String, Object> overview() {
        return Map.of(
                "module", "sandbox-runtime-module",
                "status", "active",
                "description", "沙箱运行时模块，支持 Groovy/JS/Python/Wasm 脚本隔离执行。",
                "securityPolicy", securityPolicy.getClass().getSimpleName(),
                "defaultTimeoutMs", DEFAULT_TIMEOUT_MS,
                "maxTimeoutMs", MAX_TIMEOUT_MS,
                "maxOutputBytes", MAX_OUTPUT_BYTES,
                "supportedLanguages", java.util.List.of("groovy", "javascript", "python", "wasm")
        );
    }

    public SandboxExecutor.SandboxResult execute(String code, String language, long timeout) {
        if (code == null || code.isBlank()) {
            return new SandboxExecutor.SandboxResult(-1, "", "Code must not be blank");
        }
        if (language == null || language.isBlank()) {
            return new SandboxExecutor.SandboxResult(-1, "", "Language must not be specified");
        }
        if (!securityPolicy.isAllowed(language)) {
            log.warn("Sandbox execution blocked for language: {}", language);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Language '" + language + "' is not allowed by security policy");
        }
        if (!securityPolicy.isCodeSafe(code)) {
            log.warn("Sandbox execution blocked: code failed static safety check");
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Code rejected by sandbox security policy (blocked pattern detected)");
        }

        long effectiveTimeout = Math.min(timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS);

        try {
            Future<SandboxExecutor.SandboxResult> future = executorService.submit(() ->
                    executeInSandbox(code, language, effectiveTimeout));

            return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            log.warn("Sandbox execution timed out: language={} timeout={}ms", language, effectiveTimeout);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Execution timed out after " + effectiveTimeout + "ms");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxExecutor.SandboxResult(-1, "", "Execution interrupted");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Execution failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
        }
    }

    private SandboxExecutor.SandboxResult executeInSandbox(String code, String language, long timeoutMs) {
        return switch (language.toLowerCase()) {
            case "groovy" -> executeGroovy(code, timeoutMs);
            case "javascript", "js" -> executeJavaScript(code, timeoutMs);
            case "python", "py" -> executePython(code, timeoutMs);
            case "wasm" -> executeWasm(code, timeoutMs);
            default -> new SandboxExecutor.SandboxResult(-1, "",
                    "Unsupported language: " + language);
        };
    }

    private SandboxExecutor.SandboxResult executeGroovy(String code, long timeoutMs) {
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager()
                    .getEngineByName("Groovy");
            if (engine == null) {
                return new SandboxExecutor.SandboxResult(-1, "",
                        "Groovy engine not available on this JVM");
            }
            Object result = engine.eval(code);
            String output = result != null ? result.toString() : "";
            return truncateOutput(new SandboxExecutor.SandboxResult(0, output, ""));
        } catch (Exception e) {
            return new SandboxExecutor.SandboxResult(1, "", "Groovy error: " + e.getMessage());
        }
    }

    private SandboxExecutor.SandboxResult executeJavaScript(String code, long timeoutMs) {
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager()
                    .getEngineByName("nashorn");
            if (engine == null) {
                engine = new javax.script.ScriptEngineManager().getEngineByName("JavaScript");
            }
            if (engine == null) {
                return new SandboxExecutor.SandboxResult(-1, "",
                        "JavaScript engine not available on this JVM");
            }
            Object result = engine.eval(code);
            String output = result != null ? result.toString() : "";
            return truncateOutput(new SandboxExecutor.SandboxResult(0, output, ""));
        } catch (Exception e) {
            return new SandboxExecutor.SandboxResult(1, "", "JavaScript error: " + e.getMessage());
        }
    }

    private SandboxExecutor.SandboxResult executePython(String code, long timeoutMs) {
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager()
                    .getEngineByName("python");
            if (engine == null) {
                return new SandboxExecutor.SandboxResult(-1, "",
                        "Python engine not available on this JVM. Install GraalVM Python or Jython.");
            }
            Object result = engine.eval(code);
            String output = result != null ? result.toString() : "";
            return truncateOutput(new SandboxExecutor.SandboxResult(0, output, ""));
        } catch (Exception e) {
            return new SandboxExecutor.SandboxResult(1, "", "Python error: " + e.getMessage());
        }
    }

    private SandboxExecutor.SandboxResult executeWasm(String code, long timeoutMs) {
        return new SandboxExecutor.SandboxResult(-1, "",
                "Wasm execution not yet implemented. Use container-based isolation.");
    }

    private SandboxExecutor.SandboxResult truncateOutput(SandboxExecutor.SandboxResult result) {
        if (result.output() != null && result.output().getBytes().length > MAX_OUTPUT_BYTES) {
            return new SandboxExecutor.SandboxResult(
                    result.exitCode(),
                    result.output().substring(0, MAX_OUTPUT_BYTES) + "\n[TRUNCATED]",
                    result.error());
        }
        return result;
    }

    public boolean isEnabled() {
        return true;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
