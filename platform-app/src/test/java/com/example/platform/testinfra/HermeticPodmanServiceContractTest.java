package com.example.platform.testinfra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PTEH-V1: contract tests for the repo-owned hermetic Podman service lifecycle.
 *
 * <p>These tests validate that the Podman API socket launched by
 * {@code scripts/test/podman-hermetic.sh} ( {@code podman system service --time=0} )
 * satisfies the Testcontainers runtime contract: the API responds, the negotiated
 * Docker-compatible API version is &gt;= 1.41, and — critically — the service survives
 * the historical idle window that previously caused {@code Broken pipe} failures.
 *
 * <p>Plain JUnit 5, no Spring context, no database. The socket path is read from
 * {@code DOCKER_HOST} (e.g. {@code unix:///run/user/1000/podman-hermetic.sock}).
 */
class HermeticPodmanServiceContractTest {

    /** Extract the filesystem path from a unix:// DOCKER_HOST value. */
    private static String socketPath() {
        String dockerHost = System.getenv("DOCKER_HOST");
        assertNotNull(dockerHost,
                "DOCKER_HOST must point to the hermetic podman socket (scripts/test/podman-hermetic.sh run ...)");
        assertTrue(dockerHost.startsWith("unix://"),
                "DOCKER_HOST must be a unix:// socket, got: " + dockerHost);
        return dockerHost.substring("unix://".length());
    }

    /** Issue a raw HTTP GET /_ping over the unix socket and return the response. */
    private static String ping(String path) throws IOException {
        String socket = socketPath();
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.US_ASCII)));

            ByteBuffer buffer = ByteBuffer.allocate(8192);
            StringBuilder response = new StringBuilder();
            while (channel.read(buffer) != -1) {
                buffer.flip();
                response.append(StandardCharsets.US_ASCII.decode(buffer));
                buffer.clear();
            }
            return response.toString();
        }
    }

    /** Parse the Api-Version value from an HTTP response header block. */
    private static String apiVersion(String response) {
        Matcher m = Pattern.compile("(?i)Api-Version:\\s*([\\d.]+)\\r?\\n").matcher(response);
        return m.find() ? m.group(1) : null;
    }

    @Test
    @DisplayName("docker API socket responds to /_ping with HTTP 200 and Api-Version header")
    void dockerApiSocketResponds() throws IOException {
        String response = ping("/_ping");

        assertTrue(response.startsWith("HTTP/1.1 200"),
                "Expected HTTP 200 from /_ping, got: " + response.split("\\r?\\n")[0]);

        String version = apiVersion(response);
        assertNotNull(version, "Response must contain an Api-Version header. Got:\n" + response);
    }

    @Test
    @DisplayName("docker API survives the historical idle window (old --time=5 churn)")
    void dockerApiSurvivesHistoricalIdleWindow() throws IOException, InterruptedException {
        // Historical root cause: a socket-activated podman service with the default
        // --time=5 idle timeout would exit after ~5s of no connections, and the next
        // Testcontainers call hit a dead socket -> "Broken pipe". The hermetic launcher
        // runs --time=0 (no idle exit). This probes past that window to prove it.
        String first = ping("/_ping");
        assertTrue(first.startsWith("HTTP/1.1 200"), "first /_ping should succeed");

        Thread.sleep(7000); // sleep 7s, past the old 5s idle-exit threshold

        String second = ping("/_ping");
        assertTrue(second.startsWith("HTTP/1.1 200"),
                "second /_ping (after 7s idle) must succeed — service must not idle-churn");
    }

    @Test
    @DisplayName("docker API version is negotiated and >= 1.41 (podman compat)")
    void dockerApiVersionNegotiated() throws IOException {
        String response = ping("/_ping");
        String version = apiVersion(response);
        assertNotNull(version, "Api-Version header must be present");

        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        // podman's docker-compatible API reports 1.41; assert negotiated >= 1.41.
        assertTrue(major > 1 || (major == 1 && minor >= 41),
                "Negotiated Api-Version must be >= 1.41, got: " + version);
    }
}
