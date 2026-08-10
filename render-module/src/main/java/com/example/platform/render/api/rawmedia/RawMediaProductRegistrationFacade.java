package com.example.platform.render.api.rawmedia;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.storage.contract.StorageClass;
import com.example.platform.storage.contract.StorageProviderType;
import com.example.platform.storage.contract.StorageReference;
import com.example.platform.render.infrastructure.asset.AssetRepository;
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
    private final StorageRuntimeService storageRuntimeService;
    private final AssetRepository assetRepository;
    private final Path storageRoot;

    public RawMediaProductRegistrationFacade(ProductRuntimeService productRuntimeService,
                                             StorageRuntimeService storageRuntimeService,
                                             AssetRepository assetRepository,
                                             Path storageRoot) {
        this.productRuntimeService = productRuntimeService;
        this.storageRuntimeService = storageRuntimeService;
        this.assetRepository = assetRepository;
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    @Transactional
    public RawMediaProductRegistrationResult registerRawMedia(RawMediaProductRegistrationCommand command) {
        Asset asset = assetRepository.findById(command.tenantId(), command.assetId())
                .orElseThrow(() -> new IllegalArgumentException("RAW_MEDIA asset not found: " + command.assetId()));
        if (!command.projectId().equals(asset.projectId())) {
            throw new IllegalArgumentException("RAW_MEDIA asset project mismatch: " + command.assetId());
        }

        StorageObjectRef storageRefParts = parseStorageReference(command.storageReferenceUri());
        if (!"uploads".equals(storageRefParts.bucket())) {
            throw new IllegalArgumentException("RAW_MEDIA upload bucket mismatch: " + storageRefParts.bucket());
        }
        if (!asset.storageKey().equals(storageRefParts.objectKey())) {
            throw new IllegalArgumentException("RAW_MEDIA asset storage key mismatch: " + command.assetId());
        }

        Path uploadedFile = storageRoot.resolve(storageRefParts.bucket()).resolve(storageRefParts.objectKey()).normalize();
        if (!uploadedFile.startsWith(storageRoot.resolve(storageRefParts.bucket()).normalize())) {
            throw new IllegalArgumentException("RAW_MEDIA storage key escapes upload bucket");
        }
        if (!Files.isRegularFile(uploadedFile)) {
            throw new IllegalArgumentException("RAW_MEDIA uploaded object not found: " + storageRefParts.objectKey());
        }
        long fileSize;
        try {
            fileSize = Files.size(uploadedFile);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("RAW_MEDIA uploaded object unreadable: " + storageRefParts.objectKey(), e);
        }
        if (fileSize == 0L) {
            throw new IllegalArgumentException("RAW_MEDIA uploaded object is empty: " + storageRefParts.objectKey());
        }
        String checksum = computeSha256(uploadedFile);

        StorageReference registeredStorage = storageRuntimeService.register(new StorageReference(
                null,
                StorageProviderType.LOCAL.name(),
                StorageClass.STANDARD,
                storageRoot.resolve(storageRefParts.bucket()).toString(),
                storageRefParts.objectKey(),
                checksum,
                checksum,
                fileSize,
                command.mimeType(),
                Instant.now(),
                Instant.now()));

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

    private static StorageObjectRef parseStorageReference(String storageReferenceUri) {
        return BlobStorage.parseUri(storageReferenceUri)
                .orElseThrow(() -> new IllegalArgumentException("RAW_MEDIA storage reference is invalid"));
    }

    private static String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("RAW_MEDIA checksum computation failed: " + file, e);
        }
    }
}
