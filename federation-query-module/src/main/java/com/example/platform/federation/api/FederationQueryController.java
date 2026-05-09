package com.example.platform.federation.api;

import com.example.platform.federation.app.FederationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/federation/query")
public class FederationQueryController {
    private final FederationQueryService service;

    public FederationQueryController(FederationQueryService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
