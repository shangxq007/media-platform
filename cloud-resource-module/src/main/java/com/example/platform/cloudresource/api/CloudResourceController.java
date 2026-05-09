package com.example.platform.cloudresource.api;

import com.example.platform.cloudresource.app.CloudResourceCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cloud-resources")
public class CloudResourceController {

    private final CloudResourceCatalogService cloudResourceCatalogService;

    public CloudResourceController(CloudResourceCatalogService cloudResourceCatalogService) {
        this.cloudResourceCatalogService = cloudResourceCatalogService;
    }

    @GetMapping("/providers")
    public List<String> providers() {
        return cloudResourceCatalogService.providerCodes();
    }
}
