package com.example.platform.secrets.api;

import com.example.platform.secrets.app.SecretService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretsController {

    private final SecretService secretService;

    public SecretsController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping("/resolve")
    public Map<String, String> resolve(@RequestParam String ref) {
        return Map.of("ref", ref, "value", secretService.resolve(ref));
    }
}
