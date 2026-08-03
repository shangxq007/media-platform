package com.example.platform.render.api.rawmedia;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RawMediaProductRegistrationFacadeTest {

    @Test
    void registerRawMediaBuildsRegisteredRawMediaProductInsideRenderBoundary() {
        ProductRuntimeService productRuntimeService = mock(ProductRuntimeService.class);
        Instant createdAt = Instant.now();
        when(productRuntimeService.register(argThat(product ->
                product.tenantId().equals("t1")
                        && product.projectId().equals("p1")
                        && product.ownerAssetId().equals("asset-1")
                        && product.productType() == ProductType.RAW_MEDIA
                        && product.representationKind() == RepresentationKind.MEDIA_FILE
                        && product.status() == ProductStatus.REGISTERED
                        && product.storageReferenceId().equals("localFs://uploads/key")
                        && product.mimeType().equals("video/mp4")
                        && "user-upload".equals(product.producerType()))))
                .thenReturn(new Product(
                        "prod-1", "t1", "p1", "asset-1",
                        ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                        "user-upload", null, null, ProductStatus.REGISTERED,
                        "localFs://uploads/key", null, null, "video/mp4", 1,
                        null, createdAt, createdAt));

        RawMediaProductRegistrationFacade facade = new RawMediaProductRegistrationFacade(productRuntimeService);
        RawMediaProductRegistrationResult result = facade.registerRawMedia(
                new RawMediaProductRegistrationCommand("t1", "p1", "asset-1", "localFs://uploads/key", "video/mp4"));

        assertEquals("prod-1", result.productId());
        assertEquals(createdAt, result.createdAt());
        verify(productRuntimeService).register(any(Product.class));
    }
}
