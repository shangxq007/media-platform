package com.example.platform.render.testsupport.fakes;

import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.infrastructure.product.ProductRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for {@link ProductRepository}.
 *
 * <p>Provides real repository semantics without jOOQ or database.
 * Used by fake-based smoke/integration tests.</p>
 */
public class FakeProductRepository extends ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();
    private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();
    private final Map<String, List<Product>> byAsset = new ConcurrentHashMap<>();
    private final Map<String, Map<ProductType, Product>> latestByAssetAndType = new ConcurrentHashMap<>();
    private final Map<String, List<Product>> byTimelineRevision = new ConcurrentHashMap<>();

    public FakeProductRepository() { super(); }

    @Override
    public Product save(Product p) {
        String id = p.productId() != null ? p.productId() : "prod-" + UUID.randomUUID();
        Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                p.metadataJson(), p.createdAt(), p.updatedAt());
        store.put(id, saved);
        if (p.projectId() != null) {
            byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>());
            byProject.get(p.projectId()).removeIf(e -> e.productId().equals(id));
            byProject.get(p.projectId()).add(saved);
        }
        if (p.ownerAssetId() != null) {
            byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>());
            byAsset.get(p.ownerAssetId()).removeIf(e -> e.productId().equals(id));
            byAsset.get(p.ownerAssetId()).add(saved);
            latestByAssetAndType.computeIfAbsent(p.ownerAssetId(), k -> new ConcurrentHashMap<>())
                    .put(p.productType(), saved);
        }
        if (p.sourceTimelineRevisionId() != null) {
            byTimelineRevision.computeIfAbsent(p.sourceTimelineRevisionId(), k -> new ArrayList<>());
            byTimelineRevision.get(p.sourceTimelineRevisionId()).removeIf(e -> e.productId().equals(id));
            byTimelineRevision.get(p.sourceTimelineRevisionId()).add(saved);
        }
        return saved;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findByProject(String projectId, int limit) {
        List<Product> products = byProject.getOrDefault(projectId, List.of());
        return products.size() > limit ? products.subList(0, limit) : products;
    }

    @Override
    public List<Product> findByAsset(String assetId) {
        return byAsset.getOrDefault(assetId, List.of());
    }

    @Override
    public Optional<Product> findLatest(String assetId, ProductType type) {
        Map<ProductType, Product> byType = latestByAssetAndType.get(assetId);
        return byType != null ? Optional.ofNullable(byType.get(type)) : Optional.empty();
    }

    @Override
    public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
        return byTimelineRevision.getOrDefault(timelineRevisionId, List.of());
    }

    /** Convenience: number of stored products. */
    public int size() { return store.size(); }
}
