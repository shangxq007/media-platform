package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.delivery.spi.DeliveryContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebDavDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebDavDeliveryAdapter.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public DeliveryProtocol protocol() {
        return DeliveryProtocol.WEBDAV;
    }

    @Override
    public ProbeResult probe(DeliveryContext context) {
        try {
            URI base = baseUri(context);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(base)
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", basicAuth(context))
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 500) {
                return ProbeResult.success();
            }
            return ProbeResult.failure("WebDAV OPTIONS status=" + response.statusCode());
        } catch (Exception e) {
            return ProbeResult.failure(e.getMessage());
        }
    }

    @Override
    public DeliveryResult deliver(DeliveryContext context) {
        try {
            byte[] bytes = context.sourceStream().readAllBytes();
            URI target = targetUri(context);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(target)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .timeout(Duration.ofMinutes(30))
                    .header("Authorization", basicAuth(context))
                    .header("Content-Type", context.contentType() != null ? context.contentType() : "application/octet-stream")
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("WebDAV delivery job={} uri={} bytes={}", context.deliveryJobId(), target, bytes.length);
                return DeliveryResult.ok(context.remotePath(), target.toString(), bytes.length);
            }
            return DeliveryResult.fail("WebDAV PUT status=" + response.statusCode());
        } catch (Exception e) {
            log.warn("WebDAV delivery failed job={}: {}", context.deliveryJobId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private static URI baseUri(DeliveryContext context) {
        String baseUrl = DeliveryConfigParser.stringVal(context.destinationConfig(), "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("WebDAV requires baseUrl");
        }
        return URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    }

    private static URI targetUri(DeliveryContext context) {
        URI base = baseUri(context);
        String path = context.remotePath().replace('\\', '/');
        return base.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    private static String basicAuth(DeliveryContext context) {
        Map<String, String> creds = context.credentials();
        String user = creds.getOrDefault("username",
                DeliveryConfigParser.stringVal(context.destinationConfig(), "username"));
        String pass = creds.getOrDefault("password", "");
        if (user == null || user.isBlank()) {
            return "";
        }
        String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
