package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/artifact/catalog")
public class ArtifactController {
    private final ArtifactCatalogService service;

    public ArtifactController(ArtifactCatalogService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
