package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.delivery.spi.DeliveryContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Uploads artifact bytes via HTTP PUT to a pre-configured URL (signed upload / customer endpoint).
 */
@Component
public class HttpsPutDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpsPutDeliveryAdapter.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public DeliveryProtocol protocol() {
        return DeliveryProtocol.HTTPS_PUT;
    }

    @Override
    public ProbeResult probe(DeliveryContext context) {
        try {
            URI target = resolveUploadUri(context, "probe-" + context.deliveryJobId());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(target)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(15));
            applyHeaders(builder, context);
            HttpResponse<Void> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 500) {
                return ProbeResult.success();
            }
            return ProbeResult.failure("HTTPS PUT HEAD status=" + response.statusCode());
        } catch (Exception e) {
            return ProbeResult.failure(e.getMessage());
        }
    }

    @Override
    public DeliveryResult deliver(DeliveryContext context) {
        try {
            byte[] bytes = context.sourceStream().readAllBytes();
            URI target = resolveUploadUri(context, context.sourceFileName());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(target)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .timeout(Duration.ofMinutes(30))
                    .header("Content-Type",
                            context.contentType() != null ? context.contentType() : "application/octet-stream");
            applyHeaders(builder, context);
            HttpResponse<Void> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("HTTPS_PUT delivery job={} uri={} bytes={}", context.deliveryJobId(), target, bytes.length);
                return DeliveryResult.ok(context.remotePath(), target.toString(), bytes.length);
            }
            return DeliveryResult.fail("HTTPS PUT status=" + response.statusCode());
        } catch (Exception e) {
            log.warn("HTTPS_PUT delivery failed job={}: {}", context.deliveryJobId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private static URI resolveUploadUri(DeliveryContext context, String fileName) {
        String template = DeliveryConfigParser.stringVal(context.destinationConfig(), "uploadUrl");
        if (template.isBlank()) {
            template = context.credentials().get("uploadUrl");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("HTTPS_PUT requires uploadUrl in config or credentials");
        }
        String path = context.remotePath() != null ? context.remotePath() : fileName;
        String resolved = template
                .replace("{remotePath}", path)
                .replace("{fileName}", fileName != null ? fileName : "output.bin")
                .replace("{jobId}", context.renderJobId() != null ? context.renderJobId() : "");
        return URI.create(resolved);
    }

    private static void applyHeaders(HttpRequest.Builder builder, DeliveryContext context) {
        Map<String, String> creds = context.credentials();
        String auth = creds.get("authorization");
        if (auth != null && !auth.isBlank()) {
            builder.header("Authorization", auth);
        }
        String apiKeyHeader = creds.getOrDefault("apiKeyHeader", "X-Api-Key");
        String apiKey = creds.get("apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header(apiKeyHeader, apiKey);
        }
        creds.forEach((k, v) -> {
            if (k.startsWith("header.") && v != null && !v.isBlank()) {
                builder.header(k.substring("header.".length()), v);
            }
        });
    }
}
