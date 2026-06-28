package com.example.platform.render.app.caption;

import com.example.platform.render.api.dto.CaptionTemplateDeliveryStatus;
import com.example.platform.render.api.dto.CaptionTemplateRenderResultLookupResponse;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Safe result lookup for caption template render outputs.
 *
 * <p>Internal service — does not expose storage/provider internals.</p>
 *
 * <p>v0.2: Returns outputProductId, status, productType. downloadAvailable=false.</p>
 */
@Service
public class CaptionTemplateResultLookupService {

    private static final Logger log = LoggerFactory.getLogger(CaptionTemplateResultLookupService.class);

    private final ProductRuntimeService productRuntime;

    public CaptionTemplateResultLookupService(ProductRuntimeService productRuntime) {
        this.productRuntime = productRuntime;
    }

    /**
     * Look up the result/delivery contract for an output product.
     */
    public CaptionTemplateRenderResultLookupResponse lookup(String outputProductId) {
        if (outputProductId == null || outputProductId.isBlank()) {
            return CaptionTemplateRenderResultLookupResponse.notFound();
        }

        var productOpt = productRuntime.find(outputProductId);
        if (productOpt.isEmpty()) {
            return CaptionTemplateRenderResultLookupResponse.notFound();
        }

        Product product = productOpt.get();

        // Verify product is a deliverable type
        if (product.productType() != ProductType.FINAL_RENDER) {
            return CaptionTemplateRenderResultLookupResponse.notDeliverable(
                    outputProductId,
                    "Product is not a FINAL_RENDER type: " + product.productType());
        }

        // Check status
        if (product.status() == ProductStatus.READY) {
            log.info("Caption template result lookup: product={} status=READY", outputProductId);
            return CaptionTemplateRenderResultLookupResponse.ready(
                    outputProductId, product.productType().name());
        }

        if (product.status() == ProductStatus.FAILED) {
            return new CaptionTemplateRenderResultLookupResponse(
                    outputProductId, CaptionTemplateDeliveryStatus.FAILED, false,
                    product.productType().name(), false, false, null,
                    "Product failed.");
        }

        // Other statuses
        return CaptionTemplateRenderResultLookupResponse.notDeliverable(
                outputProductId,
                "Product is not ready: " + product.status());
    }
}
