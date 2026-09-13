package com.example.platform.render.api.rawmedia;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.storage.api.StorageRuntime;
import com.example.platform.media.api.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.storage.contract.StorageClass;
import com.example.platform.storage.contract.StorageProviderType;
import com.example.platform.storage.contract.StorageReference;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purpose-specific render API for registering user-uploaded raw media products.
 *
 * <p>Keeps Product Runtime aggregate types inside the render module while allowing
 * ingest to preserve its upload pipeline through a small boundary command/result.
 */
@Service
public class RawMediaProductRegistrationFacade {

    private final ProductRuntimeService productRuntimeService;
    private final com.example.platform.storage.api.StorageFilePort storageFiles;
    private final MediaAssets assetRepository;

    public RawMediaProductRegistrationFacade(ProductRuntimeService productRuntimeService,
                                             com.example.platform.storage.api.StorageFilePort storageFiles,
                                             MediaAssets assetRepository) {
        this.productRuntimeService = productRuntimeService;
        this.storageFiles = storageFiles;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public RawMediaProductRegistrationResult registerRawMedia(RawMediaProductRegistrationCommand command) {
        Asset asset = assetRepository.findById(command.tenantId(), command.assetId())
                .orElseThrow(() -> new IllegalArgumentException("RAW_MEDIA asset not found: " + command.assetId()));
        if (!command.projectId().equals(asset.projectId())) {
            throw new IllegalArgumentException("RAW_MEDIA asset project mismatch: " + command.assetId());
        }

        StorageReference registeredStorage = storageFiles.registerUpload(
                new com.example.platform.storage.api.StorageOwnershipScope(command.tenantId(),command.projectId()),
                command.storageReferenceUri(),asset.storageKey(),command.mimeType());
        String checksum=registeredStorage.checksum();

        Product product = new Product(
                null,
                command.tenantId(),
                command.projectId(),
                command.assetId(),
                ProductType.RAW_MEDIA,
                RepresentationKind.MEDIA_FILE,
                "user-upload",
                null,
                null,
                ProductStatus.REGISTERED,
                registeredStorage.storageReferenceId(),
                checksum,
                checksum,
                command.mimeType(),
                1,
                null,
                null,
                null
        );

        Product registered = productRuntimeService.register(product);
        Product ready = productRuntimeService.markReady(registered.productId());
        return new RawMediaProductRegistrationResult(ready.productId(), ready.createdAt());
    }

}
