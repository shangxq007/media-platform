package com.example.platform.web.assets;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductRuntimeService service;

    public ProductController(ProductRuntimeService service) { this.service = service; }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDto> get(@PathVariable String productId) {
        return service.find(productId).map(p -> ResponseEntity.ok(toDto(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/products")
    public List<ProductDto> listByProject(@PathVariable String projectId, @RequestParam(defaultValue = "50") int limit) {
        return service.findByProject(projectId, limit).stream().map(ProductController::toDto).toList();
    }

    @GetMapping("/assets/{assetId}/products")
    public List<ProductDto> listByAsset(@PathVariable String assetId) {
        return service.findByAsset(assetId).stream().map(ProductController::toDto).toList();
    }

    @GetMapping("/products/{productId}/dependencies")
    public List<Map<String, String>> getDependencies(@PathVariable String productId) {
        return service.findDependencies(productId).stream()
                .map(d -> Map.of("dependencyId", d.dependencyId(), "dependsOnId", d.dependsOnProductId(),
                        "type", d.dependencyType().name(), "createdAt", d.createdAt() != null ? d.createdAt().toString() : ""))
                .toList();
    }

    @PostMapping("/products/{productId}/dependencies")
    public ResponseEntity<Map<String, String>> linkDependency(@PathVariable String productId,
            @RequestBody LinkRequest body) {
        try {
            var dep = service.linkDependency(productId, body.dependsOnProductId(),
                    DependencyType.valueOf(body.dependencyType()), "system", body.projectId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("dependencyId", dep.dependencyId(), "status", "linked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/products/{productId}/dependencies/{dependencyId}")
    public ResponseEntity<Map<String, String>> unlink(@PathVariable String productId,
            @PathVariable String dependencyId) {
        service.unlinkDependency(dependencyId);
        return ResponseEntity.ok(Map.of("status", "unlinked"));
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
