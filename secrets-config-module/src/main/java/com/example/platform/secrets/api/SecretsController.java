package com.example.platform.secrets.api;

import com.example.platform.secrets.app.SecretService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretsController {

    private final SecretService secretService;
    private final boolean secretsEndpointEnabled;

    public SecretsController(SecretService secretService,
                             @Value("${app.secrets.endpoint-enabled:false}") boolean secretsEndpointEnabled) {
        this.secretService = secretService;
        this.secretsEndpointEnabled = secretsEndpointEnabled;
    }

    @GetMapping("/resolve")
    public ResponseEntity<Map<String, String>> resolve(@RequestParam String ref) {
        if (!secretsEndpointEnabled) {
            return ResponseEntity.notFound().build();
        }
        String value = secretService.resolve(ref);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("ref", ref, "status", "resolved"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
