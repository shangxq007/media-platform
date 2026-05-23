package com.example.platform.render.infrastructure.shotstack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class ShotstackApiClient {

    private static final Logger log = LoggerFactory.getLogger(ShotstackApiClient.class);

    private final ShotstackRenderProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public ShotstackApiClient(ShotstackRenderProviderProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    public String submitRender(ObjectNode editPayload) throws Exception {
        String url = properties.getApiUrl() + "/render";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(editPayload), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode id = body.path("response").path("id");
        if (id.isMissingNode() || id.asText().isBlank()) {
            throw new IllegalStateException("Shotstack submit missing render id: " + response.getBody());
        }
        return id.asText();
    }

    public ShotstackRenderStatus pollUntilDone(String renderId) throws Exception {
        String url = properties.getApiUrl() + "/render/" + renderId;
        HttpHeaders headers = authHeaders();
        for (int i = 0; i < properties.getMaxPollAttempts(); i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode responseNode = body.path("response");
            String status = responseNode.path("status").asText("");
            if ("done".equalsIgnoreCase(status)) {
                String renderUrl = responseNode.path("url").isMissingNode()
                        ? null
                        : responseNode.path("url").asText();
                return new ShotstackRenderStatus(true, renderUrl, null);
            }
            if ("failed".equalsIgnoreCase(status)) {
                return new ShotstackRenderStatus(false, null, responseNode.path("error").asText("failed"));
            }
            Thread.sleep(properties.getPollIntervalMs());
        }
        return new ShotstackRenderStatus(false, null, "Shotstack render timed out");
    }

    public void downloadTo(String renderUrl, Path target) throws Exception {
        byte[] bytes = restTemplate.getForObject(URI.create(renderUrl), byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Empty download from Shotstack");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        log.info("Downloaded Shotstack render to {}", target);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.set("x-shotstack-env", properties.getEnvironment());
        return headers;
    }

    public record ShotstackRenderStatus(boolean success, String renderUrl, String error) {}
}
