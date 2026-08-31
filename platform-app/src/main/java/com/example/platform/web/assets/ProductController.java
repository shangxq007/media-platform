package com.example.platform.web.assets;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductRuntimeService service;

    public ProductController(ProductRuntimeService service) { this.service = service; }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDto> get(@PathVariable String productId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped product read");
    }

    @GetMapping("/projects/{projectId}/products")
    public List<ProductDto> listByProject(@PathVariable String projectId, @RequestParam(defaultValue = "50") int limit) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped project product listing");
    }

    @GetMapping("/assets/{assetId}/products")
    public List<ProductDto> listByAsset(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset product listing");
    }

    @GetMapping("/products/{productId}/dependencies")
    public List<Map<String, String>> getDependencies(@PathVariable String productId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped product dependency read");
    }

    @PostMapping("/products/{productId}/dependencies")
    public ResponseEntity<Map<String, String>> linkDependency(@PathVariable String productId,
            @RequestBody LinkRequest body) {
        throw FailClosedAuthorization.unavailable("product dependency mutation");
    }

    @DeleteMapping("/products/{productId}/dependencies/{dependencyId}")
    public ResponseEntity<Map<String, String>> unlink(@PathVariable String productId,
            @PathVariable String dependencyId) {
        throw FailClosedAuthorization.unavailable("product dependency mutation");
    }

    private static ProductDto toDto(Product p) {
        return new ProductDto(p.productId(), p.productType().name(), p.status().name(),
                p.representationKind().name(), p.ownerAssetId(), p.producerType(),
                p.version(), p.createdAt() != null ? p.createdAt().toString() : null);
    }

    public record ProductDto(String productId, String productType, String status,
                               String representationKind, String assetId, String producerType,
                               int version, String createdAt) {}
    public record LinkRequest(String dependsOnProductId, String dependencyType, String projectId) {}
}
