package com.example.platform.render.api.rawmedia;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import org.springframework.stereotype.Service;

/**
 * Purpose-specific render API for registering user-uploaded raw media products.
 *
 * <p>Keeps Product Runtime aggregate types inside the render module while allowing
 * ingest to preserve its upload pipeline through a small boundary command/result.
 */
@Service
public class RawMediaProductRegistrationFacade {

    private final ProductRuntimeService productRuntimeService;

    public RawMediaProductRegistrationFacade(ProductRuntimeService productRuntimeService) {
        this.productRuntimeService = productRuntimeService;
    }

    public RawMediaProductRegistrationResult registerRawMedia(RawMediaProductRegistrationCommand command) {
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
                command.storageReferenceUri(),
                null,
                null,
                command.mimeType(),
                1,
                null,
                null,
                null
        );

        Product registered = productRuntimeService.register(product);
        return new RawMediaProductRegistrationResult(registered.productId(), registered.createdAt());
    }
}
