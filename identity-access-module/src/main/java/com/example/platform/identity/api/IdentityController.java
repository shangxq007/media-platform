package com.example.platform.identity.api;

import com.example.platform.identity.app.IdentityAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/identity/access")
public class IdentityController {
    private final IdentityAccessService service;

    public IdentityController(IdentityAccessService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @GetMapping("/service-accounts")
    public List<Map<String, String>> serviceAccounts() {
        return service.serviceAccounts();
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        boolean valid = service.validateApiKey(apiKey);
        return Map.of(
                "valid", valid,
                "principal", valid ? service.principalOf(apiKey) : null
        );
    }
}
