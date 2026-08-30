package com.example.platform.remoterender.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** Real TCP proof for the standalone worker's sole Phase-0 HTTP surface. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RemoteWorkerProbeHttpTest.ProbeApplication.class,
        properties = "app.remote-worker.api-key=")
class RemoteWorkerProbeHttpTest {

    @LocalServerPort int port;

    @Test
    void healthIsUnauthenticatedButExecutionFamilyFailsClosed() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        assertEquals(200, send(client, "GET", "/healthz"));
        assertEquals(200, send(client, "HEAD", "/healthz"));
        assertEquals(503, send(client, "POST", "/healthz"));
        assertEquals(503, send(client, "GET", "/api/remote-worker/workers"));
        assertEquals(503, send(client, "POST", "/api/remote-worker/register"));
        assertEquals(503, send(client, "GET", "/api/remote-worker/jobs/unknown"));
        assertEquals(503, send(client, "POST", "/api/remote-worker/jobs/unknown/cancel"));
    }

    private int send(HttpClient client, String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({RemoteWorkerHealthController.class, WorkerApiKeyFilter.class})
    static class ProbeApplication {}
}
