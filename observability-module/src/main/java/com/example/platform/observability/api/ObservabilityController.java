package com.example.platform.observability.api;

import com.example.platform.observability.app.ObservabilityOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/observability")
public class ObservabilityController {
    private final ObservabilityOverviewService service;

    public ObservabilityController(ObservabilityOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
