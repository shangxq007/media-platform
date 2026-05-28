package com.example.platform.sandbox.app;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpSandboxWorkerAdapterTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsSuccessfulResponse() {
        setupHandler("/v1/sandbox/execute", 200,
                """
                {"status":"SUCCESS","stdout":"hello\\n","stderr":"","exitCode":0,"durationMs":123,"truncated":false,"workerId":"w1","runtime":"python:3.12"}
                """);

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerRequest req = new SandboxWorkerRequest("python", "print('hello')", 5000, 1024*1024, java.util.Map.of());

        SandboxWorkerResult result = adapter.execute(req);

        assertEquals(SandboxWorkerResult.Status.SUCCESS, result.status());
        assertTrue(result.stdout().contains("hello"));
        assertEquals(0, result.exitCode());
        assertEquals(123, result.durationMs());
        assertEquals("w1", result.workerId());
    }

    @Test
    void maps400ToDenied() {
        setupHandler("/v1/sandbox/execute", 400, "Bad request");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.DENIED, result.status());
    }

    @Test
    void maps403ToDenied() {
        setupHandler("/v1/sandbox/execute", 403, "Forbidden");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.DENIED, result.status());
    }

    @Test
    void maps504ToTimeout() {
        setupHandler("/v1/sandbox/execute", 504, "Gateway timeout");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.TIMEOUT, result.status());
    }

    @Test
    void maps500ToError() {
        setupHandler("/v1/sandbox/execute", 500, "Internal error");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.ERROR, result.status());
    }

    @Test
    void mapsConnectionRefusedToWorkerUnavailable() {
        // Use a port that nothing is listening on
        HttpSandboxWorkerAdapter adapter = createAdapter(port + 10000);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("Cannot connect") || result.message().contains("I/O error"));
    }

    @Test
    void mapsInvalidJsonToError() {
        setupHandler("/v1/sandbox/execute", 200, "not valid json{{{");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("Invalid response"));
    }

    @Test
    void truncatesLongOutput() {
        String longOutput = "x".repeat(2_000_000); // 2MB
        setupHandler("/v1/sandbox/execute", 200,
                "{\"status\":\"SUCCESS\",\"stdout\":\"" + longOutput + "\",\"exitCode\":0}");

        SandboxProperties.WorkerProperties workerProps =
                new SandboxProperties.WorkerProperties("http://localhost:" + port, 1000, 5000);
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                java.util.List.of("python"), 5, 1024, // 1KB max
                workerProps);
        HttpSandboxWorkerAdapter adapter = new HttpSandboxWorkerAdapter(props);

        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024, java.util.Map.of()));

        assertTrue(result.stdout().length() < longOutput.length(),
                "Output should be truncated");
    }

    @Test
    void workerUrlCannotBeOverriddenByRequest() {
        // The adapter always uses the configured base-url, not anything from the request
        setupHandler("/v1/sandbox/execute", 200,
                "{\"status\":\"SUCCESS\",\"stdout\":\"ok\",\"exitCode\":0}");

        HttpSandboxWorkerAdapter adapter = createAdapter(port);
        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024,
                        java.util.Map.of("baseUrl", "http://evil.com")));

        assertEquals(SandboxWorkerResult.Status.SUCCESS, result.status(),
                "Request should go to configured URL, not request metadata");
    }

    @Test
    void noBaseUrlReturnsUnavailable() {
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                java.util.List.of("python"), 5, 1024*1024,
                SandboxProperties.WorkerProperties.defaults()); // empty baseUrl
        HttpSandboxWorkerAdapter adapter = new HttpSandboxWorkerAdapter(props);

        SandboxWorkerResult result = adapter.execute(
                new SandboxWorkerRequest("python", "code", 5000, 1024*1024, java.util.Map.of()));

        assertEquals(SandboxWorkerResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("not configured"));
    }

    // ==================== Helpers ====================

    private HttpSandboxWorkerAdapter createAdapter(int targetPort) {
        SandboxProperties.WorkerProperties workerProps =
                new SandboxProperties.WorkerProperties(
                        "http://localhost:" + targetPort, 1000, 5000);
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                java.util.List.of("python", "javascript", "js"), 5, 1024*1024,
                workerProps);
        return new HttpSandboxWorkerAdapter(props);
    }

    private void setupHandler(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();
    }
}
