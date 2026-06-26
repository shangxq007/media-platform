package com.example.platform.render.app.product;

import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(ProductRuntimeService.class);
    private final ProductRepository repo;
    private final ProductDependencyRepository depRepo;

    public ProductRuntimeService(ProductRepository repo, ProductDependencyRepository depRepo) {
        this.repo = repo; this.depRepo = depRepo;
    }

    @Transactional
    public Product register(Product product) {
        if (product.status() != ProductStatus.REGISTERED) throw new IllegalArgumentException("Must be REGISTERED");
        var saved = repo.save(product);
        log.info("Product registered: id={} type={}", saved.productId(), saved.productType());
        return saved;
    }

    @Transactional
    public Product markReady(String productId) {
        var p = repo.findById(productId).orElseThrow();
        if (p.status() == ProductStatus.READY) return p;
        return repo.save(p.withStatus(ProductStatus.READY));
    }

    @Transactional
    public Product markFailed(String productId) {
        var p = repo.findById(productId).orElseThrow();
        return repo.save(p.withStatus(ProductStatus.FAILED));
    }

    public Optional<Product> find(String productId) { return repo.findById(productId); }
    public Optional<Product> findLatest(String assetId, ProductType type) { return repo.findLatest(assetId, type); }
    public List<Product> findByAsset(String assetId) { return repo.findByAsset(assetId); }
    public List<Product> findByProject(String projectId, int limit) { return repo.findByProject(projectId, limit); }

    @Transactional
    public ProductDependency linkDependency(String productId, String dependsOnId,
                                               DependencyType type, String tenantId, String projectId) {
        var dep = new ProductDependency(null, tenantId, projectId, productId, dependsOnId, type, null);
        var saved = depRepo.save(dep);
        log.info("Dependency linked: {} → {} ({})", productId, dependsOnId, type);
        return saved;
    }

    @Transactional
    public void unlinkDependency(String dependencyId) {
        depRepo.delete(dependencyId);
        log.info("Dependency unlinked: {}", dependencyId);
    }

    public List<ProductDependency> findDependencies(String productId) { return depRepo.findDependencies(productId); }
    public List<ProductDependency> findDependents(String productId) { return depRepo.findDependents(productId); }
    public List<String> findUpstream(String productId) {
        return depRepo.findDependencies(productId).stream().map(ProductDependency::dependsOnProductId).toList();
    }
    public List<String> findDownstream(String productId) {
        return depRepo.findDependents(productId).stream().map(ProductDependency::productId).toList();
    }
}
