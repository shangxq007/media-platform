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
        if (!product.hasProvenance()) throw new IllegalArgumentException(
                "Product must have provenance: ownerAssetId, producerId, or sourceTimelineRevisionId");
        var saved = repo.save(product);
        log.info("Product registered: id={} type={} provenance ok", saved.productId(), saved.productType());
        return saved;
    }

    /** One Product effect per accepted preview request. The opaque media handle is not a MediaAssetId. */
    @Transactional
    public Product registerPreview(String tenantId,String requestIdentity,com.example.platform.storage.contract.StorageReference reference) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);
        if(requestIdentity==null || !requestIdentity.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("preview request identity required");
        if(reference==null || reference.storageReferenceId()==null || reference.fileSize()<=0 || !"video/mp4".equals(reference.mimeType()))
            throw new IllegalArgumentException("accepted preview Storage reference required");
        repo.lockPreview(tenantId,requestIdentity);
        String productId="prod_preview_"+requestIdentity.substring(0,40);
        String mediaId="media_"+requestIdentity.substring(0,40);
        var existing=repo.findById(productId);
        if(existing.isPresent()) {
            var product=existing.get();
            if(!tenantId.equals(product.tenantId()) || !mediaId.equals(product.ownerAssetId())
                    || !"preview-upload".equals(product.producerType())
                    || !reference.storageReferenceId().equals(product.storageReferenceId())
                    || !Objects.equals(reference.checksum(),product.checksum()))
                throw new IllegalStateException("preview request conflicts with persisted Product");
            if(product.status()==ProductStatus.READY)return product;
            if(product.status()!=ProductStatus.REGISTERED)throw new IllegalStateException("preview Product is not retryable");
        } else {
            register(new Product(productId,tenantId,null,mediaId,ProductType.RAW_MEDIA,RepresentationKind.MEDIA_FILE,
                    "preview-upload",mediaId,null,ProductStatus.REGISTERED,reference.storageReferenceId(),
                    reference.checksum(),reference.contentHash(),reference.mimeType(),1,
                    "{\"source\":\"preview-upload\"}",null,null));
        }
        return markReady(productId);
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
    public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
        return repo.findBySourceTimelineRevisionId(timelineRevisionId);
    }

    @Transactional
    public ProductDependency linkDependency(String productId, String dependsOnId,
                                               DependencyType type, String tenantId, String projectId) {
        if (wouldCreateCycle(productId, dependsOnId)) {
            throw new IllegalArgumentException("Cycle detected: " + productId + " ← " + dependsOnId);
        }
        var dep = new ProductDependency(null, tenantId, projectId, productId, dependsOnId, type, null);
        var saved = depRepo.save(dep);
        log.info("Dependency linked: {} → {} ({})", productId, dependsOnId, type);
        return saved;
    }

    private boolean wouldCreateCycle(String productId, String dependsOnId) {
        if (productId.equals(dependsOnId)) return true;
        Set<String> upstream = new HashSet<>();
        collectUpstream(dependsOnId, upstream);
        return upstream.contains(productId);
    }

    private void collectUpstream(String productId, Set<String> visited) {
        if (!visited.add(productId)) return;
        for (var dep : depRepo.findDependencies(productId)) {
            collectUpstream(dep.dependsOnProductId(), visited);
        }
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
