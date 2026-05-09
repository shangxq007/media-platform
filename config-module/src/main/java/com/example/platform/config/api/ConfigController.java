package com.example.platform.config.api;

import com.example.platform.config.api.dto.UpsertConfigRequest;
import com.example.platform.config.app.ConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configs")
public class ConfigController {
    private final ConfigService service;
    public ConfigController(ConfigService service) { this.service = service; }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "system") String namespace) {
        return service.list(namespace);
    }

    @PostMapping
    public void upsert(@Valid @RequestBody UpsertConfigRequest request) {
        service.upsert(request.namespaceKey(), request.configKey(), request.valueJson());
    }
}