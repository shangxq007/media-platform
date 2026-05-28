package com.example.platform.sandbox.app;

import com.example.platform.sandbox.domain.SandboxExecutor;
import com.example.platform.sandbox.domain.SandboxSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class SandboxRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(SandboxRuntimeService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("SANDBOX_AUDIT");

    private final SandboxSecurityPolicy securityPolicy;
    private final SandboxProperties properties;
    private final SandboxWorkerPort externalWorker;
    private final ExecutorService executorService;

    public SandboxRuntimeService(SandboxSecurityPolicy securityPolicy,
                                  SandboxProperties properties,
                                  SandboxWorkerPort externalWorker) {
        this.securityPolicy = securityPolicy;
        this.properties = properties;
        this.externalWorker = externalWorker;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sandbox-runtime");
            t.setDaemon(true);
            return t;
        });
    }

    public Map<String, Object> overview() {
        SandboxExecutionMode mode = properties.executionMode();
        String workerUrl = properties.worker().baseUrl();
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("module", "sandbox-runtime-module");
        result.put("status", properties.enabled() ? "active" : "disabled");
        result.put("description", "Sandbox runtime module. Production: use external worker. Dev/test: in-process configurable.");
        result.put("securityPolicy", securityPolicy.getClass().getSimpleName());
        result.put("enabled", properties.enabled());
        result.put("executionMode", mode != null ? mode.name() : "DISABLED");
        result.put("allowInProcessEval", properties.allowInProcessEval());
        result.put("allowedLanguages", properties.allowedLanguages());
        result.put("workerConfigured", workerUrl != null && !workerUrl.isBlank());
        result.put("maxExecutionSeconds", properties.maxExecutionSeconds());
        result.put("maxOutputBytes", properties.maxOutputBytes());
        return result;
    }

    public SandboxExecutor.SandboxResult execute(String code, String language, long timeout) {
        // 1. Check master switch
        if (!properties.enabled()) {
            auditLog.info("event=sandbox_execute result=DENIED reason=module_disabled language={}", language);
            return new SandboxExecutor.SandboxResult(-1, "", "Sandbox module is disabled");
        }

        // 2. Validate inputs
        if (code == null || code.isBlank()) {
            auditLog.info("event=sandbox_execute result=DENIED reason=blank_code");
            return new SandboxExecutor.SandboxResult(-1, "", "Code must not be blank");
        }
        if (language == null || language.isBlank()) {
            auditLog.info("event=sandbox_execute result=DENIED reason=blank_language");
            return new SandboxExecutor.SandboxResult(-1, "", "Language must not be blank");
        }

        // 3. Check allowed languages from config
        if (!isLanguageAllowed(language)) {
            auditLog.info("event=sandbox_execute result=DENIED reason=language_not_allowed language={}", language);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Language '" + language + "' is not in the allowed languages list");
        }

        // 4. Route based on execution mode
        SandboxExecutionMode mode = properties.executionMode();
        if (mode == null) mode = SandboxExecutionMode.DISABLED;

        return switch (mode) {
            case DISABLED -> {
                auditLog.info("event=sandbox_execute result=DENIED reason=execution_mode_disabled language={}", language);
                yield new SandboxExecutor.SandboxResult(-1, "", "Sandbox execution is disabled");
            }
            case IN_PROCESS -> executeInProcess(code, language, timeout);
            case EXTERNAL -> executeExternal(code, language, timeout);
        };
    }

    // ==================== In-process execution (dev/test only) ====================

    private SandboxExecutor.SandboxResult executeInProcess(String code, String language, long timeout) {
        // In-process requires explicit opt-in
        if (!properties.allowInProcessEval()) {
            auditLog.info("event=sandbox_execute result=DENIED reason=in_process_eval_disabled language={}", language);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "In-process code execution is disabled. Use external sandbox worker.");
        }

        // Security policy checks (dev/test only — blocklist is bypassable)
        if (!securityPolicy.isAllowed(language)) {
            auditLog.info("event=sandbox_execute result=DENIED reason=security_policy_language language={}", language);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Language '" + language + "' is not allowed by security policy");
        }
        if (!securityPolicy.isCodeSafe(code)) {
            auditLog.info("event=sandbox_execute result=DENIED reason=unsafe_code language={} codeHash={} codeLength={}",
                    language, hash(code), code.length());
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Code rejected by sandbox security policy (blocked pattern detected)");
        }

        long effectiveTimeout = Math.min(
                Math.max(timeout > 0 ? timeout : properties.maxExecutionSeconds() * 1000L, 1000),
                120_000);

        auditLog.info("event=sandbox_execute result=ATTEMPT mode=IN_PROCESS language={} codeHash={} codeLength={} timeoutMs={}",
                language, hash(code), code.length(), effectiveTimeout);

        try {
            Future<SandboxExecutor.SandboxResult> future = executorService.submit(() ->
                    executeInSandbox(code, language));
            SandboxExecutor.SandboxResult result = future.get(effectiveTimeout, TimeUnit.MILLISECONDS);

            if (result.exitCode() == 0) {
                auditLog.info("event=sandbox_execute result=SUCCESS mode=IN_PROCESS language={} codeHash={}",
                        language, hash(code));
            } else {
                auditLog.info("event=sandbox_execute result=FAILED mode=IN_PROCESS language={} codeHash={} error={}",
                        language, hash(code), result.error());
            }
            return result;

        } catch (TimeoutException e) {
            auditLog.warn("event=sandbox_execute result=TIMEOUT mode=IN_PROCESS language={} codeHash={} timeoutMs={}",
                    language, hash(code), effectiveTimeout);
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Execution timed out after " + effectiveTimeout + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auditLog.warn("event=sandbox_execute result=INTERRUPTED mode=IN_PROCESS language={} codeHash={}",
                    language, hash(code));
            return new SandboxExecutor.SandboxResult(-1, "", "Execution interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            auditLog.warn("event=sandbox_execute result=ERROR mode=IN_PROCESS language={} codeHash={} error={}",
                    language, hash(code), cause != null ? cause.getMessage() : e.getMessage());
            return new SandboxExecutor.SandboxResult(-1, "",
                    "Execution failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
        }
    }

    // ==================== External worker execution (production-safe) ====================

    private SandboxExecutor.SandboxResult executeExternal(String code, String language, long timeout) {
        long effectiveTimeout = Math.min(
                Math.max(timeout > 0 ? timeout : properties.maxExecutionSeconds() * 1000L, 1000),
                120_000);

        auditLog.info("event=sandbox_execute result=ATTEMPT mode=EXTERNAL language={} codeHash={} codeLength={} timeoutMs={}",
                language, hash(code), code.length(), effectiveTimeout);

        Map<String, String> metadata = Map.of(
                "codeHash", hash(code),
                "codeLength", String.valueOf(code.length())
        );

        SandboxWorkerRequest request = new SandboxWorkerRequest(
                language, code, effectiveTimeout, properties.maxOutputBytes(), metadata);

        SandboxWorkerResult workerResult = externalWorker.execute(request);

        // Map worker result to internal result
        SandboxExecutor.SandboxResult result = switch (workerResult.status()) {
            case SUCCESS -> {
                auditLog.info("event=sandbox_execute result=SUCCESS mode=EXTERNAL language={} codeHash={}",
                        language, hash(code));
                yield truncateOutput(new SandboxExecutor.SandboxResult(
                        0, workerResult.stdout(), workerResult.stderr()));
            }
            case FAILED -> {
                auditLog.info("event=sandbox_execute result=FAILED mode=EXTERNAL language={} codeHash={} error={}",
                        language, hash(code), workerResult.message());
                yield new SandboxExecutor.SandboxResult(
                        workerResult.exitCode(), workerResult.stdout(), workerResult.message());
            }
            case TIMEOUT -> {
                auditLog.warn("event=sandbox_execute result=TIMEOUT mode=EXTERNAL language={} codeHash={}",
                        language, hash(code));
                yield new SandboxExecutor.SandboxResult(-1, "", "Worker execution timed out: " + workerResult.message());
            }
            case DENIED -> {
                auditLog.warn("event=sandbox_execute result=DENIED mode=EXTERNAL language={} codeHash={} reason={}",
                        language, hash(code), workerResult.message());
                yield new SandboxExecutor.SandboxResult(-1, "", "Execution denied: " + workerResult.message());
            }
            case ERROR -> {
                auditLog.warn("event=sandbox_execute result=ERROR mode=EXTERNAL language={} codeHash={} error={}",
                        language, hash(code), workerResult.message());
                yield new SandboxExecutor.SandboxResult(-1, workerResult.stderr(),
                        "Worker error: " + workerResult.message());
            }
            default -> {
                auditLog.warn("event=sandbox_execute result=ERROR mode=EXTERNAL language={} codeHash={} unknown_status={}",
                        language, hash(code), workerResult.status());
                yield new SandboxExecutor.SandboxResult(-1, "", "Unknown worker status: " + workerResult.status());
            }
        };

        return result;
    }

    // ==================== Helpers ====================

    private boolean isLanguageAllowed(String language) {
        List<String> allowed = properties.allowedLanguages();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        String lower = language.toLowerCase().trim();
        return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(lower));
    }

    private SandboxExecutor.SandboxResult executeInSandbox(String code, String language) {
        return switch (language.toLowerCase()) {
            case "groovy" -> executeGroovy(code);
            case "javascript", "js" -> executeJavaScript(code);
            case "python", "py" -> executePython(code);
            case "wasm" -> executeWasm(code);
            default -> new SandboxExecutor.SandboxResult(-1, "",
                    "Unsupported language: " + language);
        };
    }

    private SandboxExecutor.SandboxResult executeGroovy(String code) {
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

    private SandboxExecutor.SandboxResult executeJavaScript(String code) {
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

    private SandboxExecutor.SandboxResult executePython(String code) {
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

    private SandboxExecutor.SandboxResult executeWasm(String code) {
        return new SandboxExecutor.SandboxResult(-1, "",
                "Wasm execution not yet implemented. Use container-based isolation.");
    }

    private SandboxExecutor.SandboxResult truncateOutput(SandboxExecutor.SandboxResult result) {
        int maxBytes = properties.maxOutputBytes();
        if (result.output() != null && result.output().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            return new SandboxExecutor.SandboxResult(
                    result.exitCode(),
                    result.output().substring(0, maxBytes) + "\n[TRUNCATED]",
                    result.error());
        }
        return result;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public SandboxExecutionMode getExecutionMode() {
        return properties.executionMode();
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

    private static String hash(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
