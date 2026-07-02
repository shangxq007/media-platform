package com.example.platform.render.testsupport.fakes;

import com.example.platform.render.domain.product.ProductDependency;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for {@link ProductDependencyRepository}.
 *
 * <p>Provides real dependency semantics without jOOQ or database.
 * Supports both upstream (dependencies) and downstream (dependents) lookups.</p>
 */
public class FakeProductDependencyRepository extends ProductDependencyRepository {

    private final Map<String, List<ProductDependency>> dependencies = new ConcurrentHashMap<>();
    private final Map<String, List<ProductDependency>> dependents = new ConcurrentHashMap<>();
    private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

    public FakeProductDependencyRepository() { super(); }

    @Override
    public ProductDependency save(ProductDependency dep) {
        String id = dep.dependencyId() != null ? dep.dependencyId() : "pdep-" + UUID.randomUUID();
        ProductDependency saved = new ProductDependency(id, dep.tenantId(), dep.projectId(),
                dep.productId(), dep.dependsOnProductId(), dep.dependencyType(), Instant.now());
        store.put(id, saved);
        dependencies.computeIfAbsent(dep.productId(), k -> new ArrayList<>()).add(saved);
        dependents.computeIfAbsent(dep.dependsOnProductId(), k -> new ArrayList<>()).add(saved);
        return saved;
    }

    @Override
    public List<ProductDependency> findDependencies(String productId) {
        return dependencies.getOrDefault(productId, List.of());
    }

    @Override
    public List<ProductDependency> findDependents(String productId) {
        return dependents.getOrDefault(productId, List.of());
    }

    @Override
    public boolean exists(String productId, String dependsOnId) {
        return findDependencies(productId).stream()
                .anyMatch(d -> d.dependsOnProductId().equals(dependsOnId));
    }

    @Override
    public void delete(String dependencyId) {
        ProductDependency removed = store.remove(dependencyId);
        if (removed != null) {
            dependencies.getOrDefault(removed.productId(), List.of()).removeIf(d -> d.dependencyId().equals(dependencyId));
            dependents.getOrDefault(removed.dependsOnProductId(), List.of()).removeIf(d -> d.dependencyId().equals(dependencyId));
        }
    }
}
