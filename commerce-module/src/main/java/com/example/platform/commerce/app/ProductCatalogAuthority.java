package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.*;
import com.example.platform.commerce.infrastructure.ProductCatalogJdbcRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical transactional ProductCatalog / CommercialOffering command authority. */
@Service
public class ProductCatalogAuthority {
    private final ProductCatalogJdbcRepository repository;

    public ProductCatalogAuthority(ProductCatalogJdbcRepository repository) { this.repository = repository; }

    @Transactional
    public CatalogMutationResult create(CreateCommercialOfferingCommand command) {
        String fingerprint = fingerprint(command);
        if (!repository.claimCommand(command.traceId(), command.actor(), "CREATE", command.idempotencyKey(), fingerprint,
                command.productId(), command.offeringId(), null, command.source(), command.reason(), command.traceId(), command.occurredAt())) {
            return replayCreate(command, fingerprint);
        }
        repository.insertProductIfAbsent(command);
        validateStableProduct(command, repository.findProductById(command.productId())
                .orElseThrow(() -> new IllegalStateException("stable product identity conflict")));
        repository.insertOffering(command);
        repository.completeCommand(command.actor().catalogScope(), command.idempotencyKey(), "DRAFT", 1);
        return load(command.productId(), command.offeringId());
    }

    @Transactional
    public CommercialOffering transition(LifecycleOfferingCommand command) {
        String fingerprint = fingerprint(command);
        if (!repository.claimCommand(command.traceId(), command.actor(), "LIFECYCLE", command.idempotencyKey(), fingerprint,
                null, command.offeringId(), null, command.source(), command.reason(), command.traceId(), command.occurredAt())) {
            verifyReplay(command.actor(), command.idempotencyKey(), fingerprint);
            var audit = repository.findCommand(command.actor().catalogScope(), command.idempotencyKey()).orElseThrow();
            return withState(repository.findOfferingById(command.offeringId()).orElseThrow(),
                    OfferingLifecycleState.valueOf(audit.resultState()), audit.resultVersion(), command.occurredAt());
        }
        CommercialOffering current = repository.findOfferingById(command.offeringId())
                .orElseThrow(() -> new IllegalStateException("offering not found"));
        if (!legal(current.lifecycleState(), command.targetState())) throw new IllegalStateException("illegal offering lifecycle transition");
        repository.transitionOffering(command.offeringId(), command.expectedVersion(), current.lifecycleState(), command.targetState(), command.occurredAt());
        repository.completeCommand(command.actor().catalogScope(), command.idempotencyKey(), command.targetState().name(), command.expectedVersion() + 1);
        return repository.findOfferingById(command.offeringId()).orElseThrow();
    }

    @Transactional
    public ProductCatalogEntry transitionProduct(LifecycleProductCommand command) {
        String fingerprint = fingerprint(command);
        if (!repository.claimCommand(command.traceId(), command.actor(), "PRODUCT_LIFECYCLE",
                command.idempotencyKey(), fingerprint, command.productId(), null, null,
                command.source(), command.reason(), command.traceId(), command.occurredAt())) {
            var audit = verifyReplay(command.actor(), command.idempotencyKey(), fingerprint);
            return withState(repository.findProductById(command.productId()).orElseThrow(),
                    ProductLifecycleState.valueOf(audit.resultState()), audit.resultVersion(), command.occurredAt());
        }
        ProductCatalogEntry current = repository.findProductById(command.productId())
                .orElseThrow(() -> new IllegalStateException("product not found"));
        if (!legal(current.lifecycleState(), command.targetState())) {
            throw new IllegalStateException("illegal product lifecycle transition");
        }
        repository.transitionProduct(command.productId(), command.expectedVersion(), current.lifecycleState(),
                command.targetState(), command.occurredAt());
        repository.completeCommand(command.actor().catalogScope(), command.idempotencyKey(),
                command.targetState().name(), command.expectedVersion() + 1);
        return repository.findProductById(command.productId()).orElseThrow();
    }

    @Transactional
    public ProviderProductMapping mapProvider(MapProviderOfferingCommand command) {
        String fingerprint = fingerprint(command);
        if (!repository.claimCommand(command.traceId(), command.actor(), "MAP_PROVIDER", command.idempotencyKey(), fingerprint,
                command.productId(), command.offeringId(), command.mappingId(), command.source(), command.reason(), command.traceId(), command.occurredAt())) {
            verifyReplay(command.actor(), command.idempotencyKey(), fingerprint);
            return repository.findMapping(command.mappingId()).orElseThrow();
        }
        CommercialOffering offering = repository.findOfferingById(command.offeringId()).orElseThrow();
        if (!offering.productId().equals(command.productId()) || offering.offeringVersion() != command.offeringVersion()) {
            throw new IllegalStateException("provider mapping must reference an exact product offering version");
        }
        ProviderProductMapping result = repository.insertMapping(command);
        repository.completeCommand(command.actor().catalogScope(), command.idempotencyKey(), "MAPPED", result.version());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<CommercialOffering> resolveForCheckout(CatalogReadScope scope, String market, String productCode, java.time.Instant at) {
        if (market == null || market.isBlank() || productCode == null || productCode.isBlank() || at == null) {
            throw new IllegalArgumentException("tenant, market, product and time are required");
        }
        return repository.resolveActive(scope, market, productCode, at);
    }

    @Transactional(readOnly = true)
    public java.util.List<CommercialOffering> listForCheckout(CatalogReadScope scope, String market, java.time.Instant at) {
        if (market == null || market.isBlank() || at == null) throw new IllegalArgumentException("market and time are required");
        return repository.listActive(scope, market, at);
    }

    @Transactional(readOnly = true)
    public Optional<CommercialOffering> findHistorical(CatalogReadScope scope, String offeringId, long offeringVersion) {
        return repository.findHistorical(scope, offeringId, offeringVersion);
    }

    @Transactional(readOnly = true)
    public Optional<ProductCatalogEntry> findProduct(CatalogReadScope scope, String productId) {
        if (scope == null || productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("catalog scope and product are required");
        }
        return repository.findProduct(scope, productId);
    }

    private CatalogMutationResult replayCreate(CreateCommercialOfferingCommand command, String fingerprint) {
        var audit = verifyReplay(command.actor(), command.idempotencyKey(), fingerprint);
        CatalogMutationResult current = load(audit.productId(), audit.offeringId());
        OfferingLifecycleState state = OfferingLifecycleState.valueOf(audit.resultState());
        return new CatalogMutationResult(current.product(),
                withState(current.offering(), state, audit.resultVersion(), command.occurredAt()));
    }

    private ProductCatalogJdbcRepository.CommandAudit verifyReplay(CatalogActor actor, String key, String fingerprint) {
        var audit = repository.findCommand(actor.catalogScope(), key).orElseThrow(() -> new IllegalStateException("catalog command claim lost"));
        if (!audit.fingerprint().equals(fingerprint)) throw new IllegalStateException("idempotency key payload mismatch");
        if ("PENDING".equals(audit.resultState())) throw new IllegalStateException("catalog command is still pending");
        return audit;
    }

    private CatalogMutationResult load(String productId, String offeringId) {
        return new CatalogMutationResult(repository.findProductById(productId).orElseThrow(), repository.findOfferingById(offeringId).orElseThrow());
    }

    private static CommercialOffering withState(CommercialOffering o, OfferingLifecycleState state, long version, java.time.Instant updatedAt) {
        return new CommercialOffering(o.offeringId(), o.productId(), o.productCode(), o.productLineType(), o.displayName(),
                o.offeringKey(), o.offeringVersion(),
                state, version, o.purchaseMode(), o.tenantScope(), o.marketScope(), o.validFrom(), o.validTo(),
                o.entitlementBundleReference(), o.quotaProfileReference(), o.subscriptionPlanReference(),
                o.commercialPriceReference(), o.priceSnapshot(), o.creditQuantityMinor(), o.seatQuantity(),
                o.seatFeatureKey(), o.createdAt(), updatedAt);
    }

    private static ProductCatalogEntry withState(ProductCatalogEntry p, ProductLifecycleState state, long version,
            java.time.Instant updatedAt) {
        return new ProductCatalogEntry(p.productId(), p.productCode(), p.lineType(), p.displayName(), state,
                version, p.createdAt(), updatedAt);
    }

    private static void validateStableProduct(CreateCommercialOfferingCommand command, ProductCatalogEntry product) {
        if (!product.productCode().equals(command.productCode()) || product.lineType() != command.lineType()
                || !product.displayName().equals(command.displayName())) {
            throw new IllegalStateException("offering product identity does not match stable product");
        }
    }

    private static boolean legal(OfferingLifecycleState from, OfferingLifecycleState to) {
        return (from == OfferingLifecycleState.DRAFT && to == OfferingLifecycleState.ACTIVE)
                || (from == OfferingLifecycleState.ACTIVE && to == OfferingLifecycleState.RETIRED);
    }

    private static boolean legal(ProductLifecycleState from, ProductLifecycleState to) {
        return (from == ProductLifecycleState.DRAFT && to == ProductLifecycleState.ACTIVE)
                || (from == ProductLifecycleState.ACTIVE && to == ProductLifecycleState.RETIRED);
    }

    private static String fingerprint(Object command) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(command.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
