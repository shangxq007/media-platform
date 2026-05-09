package com.example.platform.storage.api;

import com.example.platform.storage.app.StorageCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final StorageCatalogService storageCatalogService;

    public StorageController(StorageCatalogService storageCatalogService) {
        this.storageCatalogService = storageCatalogService;
    }

    @GetMapping("/providers")
    public List<String> providers() {
        return storageCatalogService.providerCodes();
    }
}
