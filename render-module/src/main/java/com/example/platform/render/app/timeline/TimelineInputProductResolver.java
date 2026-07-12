package com.example.platform.render.app.timeline;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves timeline source asset IDs to input Product IDs for render.
 *
 * <p>Takes source asset identifiers extracted from TimelineSpec clips and
 * resolves them to registered READY RAW_MEDIA Products via
 * {@link ProductRuntimeService#findByAsset(String)}.</p>
 *
 * <p>Fail-closed: any invalid source asset ID or missing Product mapping
 * produces a failure result. No silent fallback to direct file paths.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Internal application-layer service — not public API</li>
 *   <li>Does not modify ProductRuntime or StorageRuntime semantics</li>
 *   <li>Does not materialize inputs — delegates to RenderInputMaterializationService</li>
 *   <li>Rejects unsafe asset references: absolute paths, traversal, URLs, provider hints</li>
 * </ul>
 */
@Service
public class TimelineInputProductResolver {

    private static final Logger log = LoggerFactory.getLogger(TimelineInputProductResolver.class);

    private final ProductRuntimeService productRuntime;

    public TimelineInputProductResolver(ProductRuntimeService productRuntime) {
        this.productRuntime = productRuntime;
    }

    /**
     * Resolve source asset IDs to input Product IDs.
     *
     * @param sourceAssetIds the asset IDs extracted from timeline clips
     * @return the resolution result (success with Product IDs or failure with reason)
     */
    public TimelineInputProductResolverResult resolve(List<String> sourceAssetIds) {
        return resolveWithBindings(sourceAssetIds, null);
    }

    /**
     * Resolve source asset IDs to input Product IDs, with optional explicit product bindings.
     * @param sourceAssetIds the asset IDs extracted from timeline clips
     * @param productBindings optional map of assetId -> productId for explicit binding
     */
    public TimelineInputProductResolverResult resolveWithBindings(List<String> sourceAssetIds, Map<String, String> productBindings) {
        if (sourceAssetIds == null || sourceAssetIds.isEmpty()) {
            return TimelineInputProductResolverResult.failure(
                    sourceAssetIds != null ? sourceAssetIds : List.of(),
                    "No source assets in timeline");
        }

        Set<String> lowercasedInternalNames = TimelineRenderJobMapper.INTERNAL_PROVIDER_NAMES
                .stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        List<String> resolvedProductIds = new ArrayList<>();

        for (String sourceAssetId : sourceAssetIds) {
            String validationError = validateSourceAssetId(sourceAssetId, lowercasedInternalNames);
            if (validationError != null) {
                log.warn("Source asset ID validation failed: {} reason={}", sourceAssetId, validationError);
                return TimelineInputProductResolverResult.failure(sourceAssetIds, validationError);
            }

            // Try explicit binding first
            Product matched = null;
            if (productBindings != null && productBindings.containsKey(sourceAssetId)) {
                String boundProductId = productBindings.get(sourceAssetId);
                matched = productRuntime.find(boundProductId)
                        .filter(p -> p.status() == ProductStatus.READY)
                        .filter(p -> p.productType() == ProductType.RAW_MEDIA)
                        .orElse(null);
                if (matched != null) {
                    log.info("Resolved asset {} to Product {} via explicit binding", sourceAssetId, matched.productId());
                }
            }

            // Fall back to direct asset ID lookup
            if (matched == null) {
                List<Product> candidates = productRuntime.findByAsset(sourceAssetId);
                matched = candidates.stream()
                        .filter(p -> p.status() == ProductStatus.READY)
                        .filter(p -> p.productType() == ProductType.RAW_MEDIA)
                        .filter(p -> p.representationKind() == RepresentationKind.MEDIA_FILE)
                        .findFirst()
                        .orElse(null);
            }

            if (matched == null) {
                log.warn("No READY RAW_MEDIA Product found for asset: {}", sourceAssetId);
                return TimelineInputProductResolverResult.failure(sourceAssetIds,
                        "No READY RAW_MEDIA Product found for asset: " + sourceAssetId);
            }

            log.info("Resolved asset {} to Product {}", sourceAssetId, matched.productId());
            resolvedProductIds.add(matched.productId());
        }

        // De-duplicate preserving order
        List<String> dedupedProductIds = new ArrayList<>(new LinkedHashSet<>(resolvedProductIds));

        if (dedupedProductIds.isEmpty()) {
            return TimelineInputProductResolverResult.failure(sourceAssetIds,
                    "No resolvable input Products");
        }

        log.info("Input product resolution complete: {} assets → {} unique Products",
                sourceAssetIds.size(), dedupedProductIds.size());

        return TimelineInputProductResolverResult.success(dedupedProductIds, sourceAssetIds);
    }

    private String validateSourceAssetId(String sourceAssetId, Set<String> lowercasedInternalNames) {
        if (sourceAssetId == null || sourceAssetId.isBlank()) {
            return "Blank source asset ID";
        }
        if (sourceAssetId.contains("..")) {
            return "Unsafe source asset ID: path traversal: " + sourceAssetId;
        }
        if (sourceAssetId.startsWith("~")) {
            return "Unsafe source asset ID: home directory: " + sourceAssetId;
        }
        if (sourceAssetId.startsWith("/")) {
            return "Unsafe source asset ID: absolute path: " + sourceAssetId;
        }
        if (sourceAssetId.contains("\\")) {
            return "Unsafe source asset ID: backslash path: " + sourceAssetId;
        }

        String lower = sourceAssetId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file://")) {
            return "Unsafe source asset ID: file:// URI: " + sourceAssetId;
        }
        if (lower.startsWith("http://")) {
            return "Unsafe source asset ID: http URL: " + sourceAssetId;
        }
        if (lower.startsWith("https://")) {
            return "Unsafe source asset ID: https URL: " + sourceAssetId;
        }
        if (lower.startsWith("s3://")) {
            return "Unsafe source asset ID: s3 URL: " + sourceAssetId;
        }
        if (lower.startsWith("gs://")) {
            return "Unsafe source asset ID: gs URL: " + sourceAssetId;
        }

        // Exact-match against lowercased internal provider/backend names
        if (lowercasedInternalNames.contains(lower)) {
            return "Unsafe source asset ID matches internal provider/backend hint: " + sourceAssetId;
        }

        return null;
    }
}
