package com.example.platform.sandbox.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP adapter for communicating with an external sandbox worker.
 *
 * <p>Sends code execution requests to the worker's REST API and maps
 * HTTP responses/errors to {@link SandboxWorkerResult}.
 *
 * <p>Security: Does NOT log request bodies (which contain user code).
 */
@Component
@ConditionalOnProperty(prefix = "sandbox.worker", name = "base-url")
public class HttpSandboxWorkerAdapter implements SandboxWorkerPort {

    private static final Logger log = LoggerFactory.getLogger(HttpSandboxWorkerAdapter.class);
    private static final Logger auditLog = LoggerFactory.getLogger("SANDBOX_AUDIT");

    private final SandboxProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpSandboxWorkerAdapter(SandboxProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.worker().connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SandboxWorkerResult execute(SandboxWorkerRequest request) {
        String baseUrl = properties.worker().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return SandboxWorkerResult.workerUnavailable("Worker base URL is not configured");
        }

        String language = request.language();
        String codeHash = hash(request.code());

        auditLog.info("event=sandbox_worker request=EXECUTE language={} codeHash={} codeLength={}",
                language, codeHash, request.code().length());

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "language", request.language(),
                    "code", request.code(),
                    "timeoutMs", request.timeoutMs(),
                    "maxOutputBytes", request.maxOutputBytes(),
                    "metadata", request.metadata()
            ));

            URI uri = URI.create(baseUrl + "/v1/sandbox/execute");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(properties.worker().readTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            return mapResponse(httpResponse, language, codeHash);

        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            auditLog.warn("event=sandbox_worker result=WORKER_UNAVAILABLE language={} codeHash={} error={}",
                    language, codeHash, e.getMessage());
            return SandboxWorkerResult.workerUnavailable(
                    "Cannot connect to sandbox worker: " + e.getMessage());

        } catch (java.net.http.HttpTimeoutException e) {
            auditLog.warn("event=sandbox_worker result=TIMEOUT language={} codeHash={} timeoutMs={}",
                    language, codeHash, request.timeoutMs());
            return SandboxWorkerResult.timeout(request.timeoutMs());

        } catch (java.io.IOException e) {
            auditLog.warn("event=sandbox_worker result=IO_ERROR language={} codeHash={} error={}",
                    language, codeHash, e.getMessage());
            return SandboxWorkerResult.error("I/O error communicating with sandbox worker: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auditLog.warn("event=sandbox_worker result=INTERRUPTED language={} codeHash={}",
                    language, codeHash);
            return SandboxWorkerResult.error("Request interrupted");

        } catch (Exception e) {
            auditLog.warn("event=sandbox_worker result=ERROR language={} codeHash={} error={}",
                    language, codeHash, e.getMessage());
            return SandboxWorkerResult.error("Unexpected error: " + e.getMessage());
        }
    }

    private SandboxWorkerResult mapResponse(HttpResponse<String> response, String language, String codeHash) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode >= 200 && statusCode < 300) {
            try {
                WorkerResponse wr = objectMapper.readValue(body, WorkerResponse.class);
                SandboxWorkerResult.Status status = parseStatus(wr.status);

                String stdout = truncate(wr.stdout != null ? wr.stdout : "", properties.maxOutputBytes());
                String stderr = truncate(wr.stderr != null ? wr.stderr : "", properties.maxOutputBytes());

                auditLog.info("event=sandbox_worker result={} language={} codeHash={} exitCode={} durationMs={}",
                        status, language, codeHash, wr.exitCode, wr.durationMs);

                return new SandboxWorkerResult(
                        status, stdout, stderr,
                        wr.exitCode != null ? wr.exitCode : 0,
                        wr.durationMs != null ? wr.durationMs : 0,
                        wr.truncated != null ? wr.truncated : false,
                        wr.errorCode != null ? wr.errorCode : "",
                        wr.message != null ? wr.message : "",
                        wr.workerId != null ? wr.workerId : "",
                        wr.runtime != null ? wr.runtime : ""
                );
            } catch (Exception e) {
                auditLog.warn("event=sandbox_worker result=INVALID_RESPONSE language={} codeHash={} error={}",
                        language, codeHash, e.getMessage());
                return SandboxWorkerResult.error("Invalid response from sandbox worker");
            }
        }

        // HTTP error mapping
        return switch (statusCode) {
            case 400, 422 -> {
                auditLog.warn("event=sandbox_worker result=DENIED language={} codeHash={} status={}",
                        language, codeHash, statusCode);
                yield SandboxWorkerResult.denied("Worker rejected request: HTTP " + statusCode);
            }
            case 403 -> {
                auditLog.warn("event=sandbox_worker result=DENIED language={} codeHash={} status=403",
                        language, codeHash);
                yield SandboxWorkerResult.denied("Worker denied execution: HTTP 403");
            }
            case 408, 504 -> {
                auditLog.warn("event=sandbox_worker result=TIMEOUT language={} codeHash={} status={}",
                        language, codeHash, statusCode);
                yield SandboxWorkerResult.timeout(properties.worker().readTimeoutMs());
            }
            case 413 -> {
                auditLog.warn("event=sandbox_worker result=FAILED language={} codeHash={} status=413",
                        language, codeHash);
                yield SandboxWorkerResult.error("Request too large: HTTP 413");
            }
            default -> {
                auditLog.warn("event=sandbox_worker result=ERROR language={} codeHash={} status={}",
                        language, codeHash, statusCode);
                yield SandboxWorkerResult.error("Worker returned HTTP " + statusCode);
            }
        };
    }

    private static SandboxWorkerResult.Status parseStatus(String status) {
        if (status == null) return SandboxWorkerResult.Status.ERROR;
        try {
            return SandboxWorkerResult.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SandboxWorkerResult.Status.ERROR;
        }
    }

    private static String truncate(String text, int maxBytes) {
        if (text == null) return "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return text;
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n[TRUNCATED]";
    }

    private static String hash(String code) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(code.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /**
     * JSON structure expected from the sandbox worker.
     */
    record WorkerResponse(
            String status,
            String stdout,
            String stderr,
            Integer exitCode,
            Long durationMs,
            Boolean truncated,
            String errorCode,
            String message,
            String workerId,
            String runtime
    ) {}
}
