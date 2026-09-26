package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.shared.web.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/artifact/catalog")
public class ArtifactController {
    private final ArtifactCatalogService service;
    private final ArtifactProjectAuthorizationPort projectAuthorization;

    public ArtifactController(ArtifactCatalogService service,
                              ArtifactProjectAuthorizationPort projectAuthorization) {
        this.service = service;
        this.projectAuthorization = projectAuthorization;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam String projectId) {
        projectAuthorization.requireRead(TenantContext.get(), projectId);
        return service.overview();
    }
}
