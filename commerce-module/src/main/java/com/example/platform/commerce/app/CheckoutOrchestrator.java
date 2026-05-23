package com.example.platform.commerce.app;

import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.domain.*;
import com.example.platform.commerce.infrastructure.CheckoutSessionRepository;
import com.example.platform.commerce.infrastructure.PurchaseOrderRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commerce.CheckoutPaymentPort;
import com.example.platform.shared.commerce.PurchaseFulfillmentCommand;
import com.example.platform.shared.commerce.PurchaseFulfillmentPort;
import com.example.platform.shared.web.TenantGuard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    private final CommerceCatalogService catalogService;
    private final CommerceCartService commerceCartService;
    private final Optional<CheckoutSessionRepository> checkoutSessionRepository;
    private final Optional<PurchaseOrderRepository> purchaseOrderRepository;
    private final Optional<PurchaseFulfillmentPort> fulfillmentPort;
    private final Optional<CheckoutPaymentPort> checkoutPaymentPort;

    private final Map<String, CheckoutSession> inMemorySessions = new ConcurrentHashMap<>();
    private final Map<String, String> inMemorySessionUserIds = new ConcurrentHashMap<>();
    private final Map<String, String> inMemorySessionCartIds = new ConcurrentHashMap<>();
    private final Map<String, PurchaseOrderCreatedEvent> inMemoryPublishedEvents = new ConcurrentHashMap<>();

    private final Counter sessionCreatedCounter;
    private final Counter orderConfirmedCounter;
    private final Counter orderCancelledCounter;
    private final Timer checkoutDurationTimer;
    private final Timer revenueCalculationTimer;

    public CheckoutOrchestrator(
            CommerceCatalogService catalogService,
            CommerceCartService commerceCartService,
            @Autowired(required = false) CheckoutSessionRepository checkoutSessionRepository,
            @Autowired(required = false) PurchaseOrderRepository purchaseOrderRepository,
            @Autowired(required = false) PurchaseFulfillmentPort fulfillmentPort,
            @Autowired(required = false) CheckoutPaymentPort checkoutPaymentPort,
            MeterRegistry meterRegistry) {
        this.catalogService = catalogService;
        this.commerceCartService = commerceCartService;
        this.checkoutSessionRepository = Optional.ofNullable(checkoutSessionRepository);
        this.purchaseOrderRepository = Optional.ofNullable(purchaseOrderRepository);
        this.fulfillmentPort = Optional.ofNullable(fulfillmentPort);
        this.checkoutPaymentPort = Optional.ofNullable(checkoutPaymentPort);
        this.sessionCreatedCounter = Counter.builder("commerce.sessions.created")
                .description("Number of checkout sessions created")
                .register(meterRegistry);
        this.orderConfirmedCounter = Counter.builder("commerce.orders.confirmed")
                .description("Number of orders confirmed")
                .register(meterRegistry);
        this.orderCancelledCounter = Counter.builder("commerce.orders.cancelled")
                .description("Number of orders cancelled")
                .register(meterRegistry);
        this.checkoutDurationTimer = Timer.builder("commerce.checkout.duration")
                .description("Duration of checkout operations")
                .register(meterRegistry);
        this.revenueCalculationTimer = Timer.builder("commerce.revenue.calculation")
                .description("Duration of revenue calculation operations")
                .register(meterRegistry);
    }

    private boolean dbBacked() {
        return checkoutSessionRepository.isPresent();
    }

    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        return createSession(request, null);
    }

    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request, String cartId) {
        validateCheckoutRequest(request);
        String tenantId = TenantGuard.tenantOrDefault(request.tenantId());
        CanonicalProduct product = catalogService.requireProduct(request.productCode());

        if (product.purchaseMode() == PurchaseMode.SUBSCRIPTION && request.successUrl() == null) {
            throw new IllegalArgumentException("Subscription products require a success URL");
        }
        if (!catalogService.isAvailableForTenant(product, tenantId)) {
            throw new IllegalArgumentException("Product '" + request.productCode() + "' is not available for this tenant");
        }

        String purchaseMode = request.purchaseMode() != null ? request.purchaseMode() : product.purchaseModeName();

        CheckoutIntent intent = new CheckoutIntent(
                tenantId, request.productCode(), purchaseMode, request.successUrl(), request.cancelUrl());

        String userId = request.userId();
        CheckoutSession session = createCheckoutSession(intent, userId, cartId);
        sessionCreatedCounter.increment();

        long amountMinor = cartId != null ? commerceCartService.cartTotalMinor(cartId) : product.priceMinor();
        String redirectUrl = session.redirectUrl();
        String providerHint = session.providerHint();
        if (checkoutPaymentPort.isPresent()) {
            CheckoutPaymentPort.CheckoutPaymentRequest paymentRequest = new CheckoutPaymentPort.CheckoutPaymentRequest(
                    session.checkoutSessionId(),
                    tenantId,
                    userId,
                    product.productCode(),
                    amountMinor,
                    product.currencyCode(),
                    request.successUrl(),
                    request.cancelUrl(),
                    cartId);
            CheckoutPaymentPort.CheckoutPaymentSession payment =
                    checkoutPaymentPort.get().createPaymentForCheckout(paymentRequest);
            redirectUrl = payment.redirectUrl();
            providerHint = payment.providerCode() + ":" + payment.providerReference();
        }

        log.info("Created checkout session {} for tenant {} product={}",
                session.checkoutSessionId(), tenantId, request.productCode());
        return new CheckoutSessionResponse(session.checkoutSessionId(), redirectUrl, providerHint);
    }

    public CheckoutSession createCheckoutSession(CheckoutIntent intent) {
        return createCheckoutSession(intent, null, null);
    }

    public CheckoutSession createCheckoutSession(CheckoutIntent intent, String userId, String cartId) {
        String tenantId = TenantGuard.tenantOrDefault(intent.tenantId());
        CanonicalProduct product = catalogService.requireProduct(intent.canonicalProductCode());
        if (!catalogService.isAvailableForTenant(product, tenantId)) {
            throw new IllegalArgumentException("Product not available: " + intent.canonicalProductCode());
        }

        String sessionId = Ids.newId("chk");
        CheckoutSession session = new CheckoutSession(
                sessionId, tenantId, intent.canonicalProductCode(), intent.successUrl(), "internal");

        if (dbBacked()) {
            checkoutSessionRepository.get().save(session, userId, cartId);
        } else {
            inMemorySessions.put(sessionId, session);
            if (userId != null && !userId.isBlank()) {
                inMemorySessionUserIds.put(sessionId, userId);
            }
            if (cartId != null && !cartId.isBlank()) {
                inMemorySessionCartIds.put(sessionId, cartId);
            }
        }
        return session;
    }

    public PurchaseOrderCreatedEvent confirmCheckout(String sessionId) {
        return confirmCheckout(sessionId, null);
    }

    public PurchaseOrderCreatedEvent confirmCheckout(String sessionId, String userIdOverride) {
        Timer.Sample sample = Timer.start();
        try {
            CheckoutSession session = requireSession(sessionId);
            TenantGuard.assertSameTenantIfContextPresent(session.tenantId());
            CanonicalProduct product = catalogService.requireProduct(session.canonicalProductCode());
            String userId = resolveUserId(session, userIdOverride);

            String orderId = Ids.newId("ord");
            long orderValue = product.priceMinor();

            PurchaseOrderCreatedEvent event = new PurchaseOrderCreatedEvent(
                    orderId, session.tenantId(), session.canonicalProductCode(), "CONFIRMED");

            persistOrder(orderId, session, orderValue, product.currencyCode());
            completeSession(sessionId);
            if (!dbBacked()) {
                inMemoryPublishedEvents.put(orderId, event);
            }
            orderConfirmedCounter.increment();

            fulfillmentPort.ifPresent(port -> fulfillOrder(orderId, sessionId, session, userId, product, port));

            log.info("Confirmed checkout {} -> order {} product={} valueMinor={}",
                    sessionId, orderId, product.productCode(), orderValue);
            return event;
        } finally {
            sample.stop(checkoutDurationTimer);
        }
    }

    private void fulfillOrder(
            String orderId,
            String sessionId,
            CheckoutSession session,
            String userId,
            CanonicalProduct product,
            PurchaseFulfillmentPort port) {
        try {
            String cartId = resolveCartId(sessionId);
            if (cartId != null) {
                for (CanonicalProduct lineProduct : commerceCartService.resolveLines(cartId)) {
                    port.fulfill(toFulfillmentCommand(orderId, session.tenantId(), userId, lineProduct));
                }
            } else {
                port.fulfill(toFulfillmentCommand(orderId, session.tenantId(), userId, product));
            }
        } catch (Exception e) {
            log.error("Purchase fulfillment failed for order {} product {}: {}",
                    orderId, product.productCode(), e.getMessage(), e);
            throw new IllegalStateException("Fulfillment failed for order " + orderId, e);
        }
    }

    public List<PurchaseOrderCreatedEvent> getPublishedEvents() {
        return List.copyOf(inMemoryPublishedEvents.values());
    }

    public CheckoutSession getSession(String sessionId) {
        return requireSession(sessionId);
    }

    public List<CanonicalProduct> listCatalogProducts() {
        return catalogService.listProducts();
    }

    public PurchaseOrderCreatedEvent cancelCheckout(String sessionId) {
        CheckoutSession session = requireSession(sessionId);
        TenantGuard.assertSameTenantIfContextPresent(session.tenantId());
        PurchaseOrderCreatedEvent cancelledEvent = new PurchaseOrderCreatedEvent(
                Ids.newId("ord"), session.tenantId(), session.canonicalProductCode(), "CANCELLED");
        persistCancelledOrder(cancelledEvent, session);
        if (!dbBacked()) {
            inMemoryPublishedEvents.put(cancelledEvent.orderId(), cancelledEvent);
        }
        orderCancelledCounter.increment();
        log.info("Cancelled checkout session {} for tenant {}", sessionId, session.tenantId());
        return cancelledEvent;
    }

    public List<PurchaseOrderCreatedEvent> getRecentEvents(String tenantId, int limit) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        if (purchaseOrderRepository.isPresent()) {
            return purchaseOrderRepository.get().findRecentByTenant(effectiveTenant, limit).stream()
                    .map(r -> new PurchaseOrderCreatedEvent(
                            r.id(), effectiveTenant, r.canonicalProductCode(), r.orderStatus()))
                    .toList();
        }
        return inMemoryPublishedEvents.values().stream()
                .filter(event -> event.tenantId().equals(effectiveTenant))
                .limit(limit)
                .toList();
    }

    public double getTotalRevenueForTenant(String tenantId) {
        Timer.Sample sample = Timer.start();
        try {
            String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
            if (purchaseOrderRepository.isPresent()) {
                return purchaseOrderRepository.get().sumConfirmedRevenueMinor(effectiveTenant) / 100.0;
            }
            return inMemoryPublishedEvents.values().stream()
                    .filter(event -> event.tenantId().equals(effectiveTenant))
                    .filter(event -> !"CANCELLED".equals(event.orderStatus()))
                    .mapToLong(event -> catalogService.findProduct(event.canonicalProductCode())
                            .map(CanonicalProduct::priceMinor)
                            .orElse(0L))
                    .sum() / 100.0;
        } finally {
            sample.stop(revenueCalculationTimer);
        }
    }

    public long getActiveSessionsCount() {
        String tenant = com.example.platform.shared.web.TenantContext.get();
        if (tenant != null && !tenant.isBlank() && checkoutSessionRepository.isPresent()) {
            return checkoutSessionRepository.get().countActiveForTenant(tenant);
        }
        return inMemorySessions.size();
    }

    public long getActiveSessionsCount(String tenantId) {
        String effectiveTenant = TenantGuard.tenantOrDefault(tenantId);
        if (checkoutSessionRepository.isPresent()) {
            return checkoutSessionRepository.get().countActiveForTenant(effectiveTenant);
        }
        return inMemorySessions.values().stream()
                .filter(session -> effectiveTenant.equals(session.tenantId()))
                .count();
    }

    private CheckoutSession requireSession(String sessionId) {
        if (dbBacked()) {
            return checkoutSessionRepository.get().findByIdUnchecked(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Checkout session not found: " + sessionId));
        }
        CheckoutSession session = inMemorySessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Checkout session not found: " + sessionId);
        }
        return session;
    }

    private String resolveUserId(CheckoutSession session, String userIdOverride) {
        if (userIdOverride != null && !userIdOverride.isBlank()) {
            return userIdOverride;
        }
        if (dbBacked()) {
            return checkoutSessionRepository.get()
                    .findMetadata(session.checkoutSessionId())
                    .map(CheckoutSessionRepository.SessionMetadata::userId)
                    .filter(id -> id != null && !id.isBlank())
                    .orElse(session.tenantId() + "-billing-owner");
        }
        String fromSession = inMemorySessionUserIds.get(session.checkoutSessionId());
        if (fromSession != null && !fromSession.isBlank()) {
            return fromSession;
        }
        return session.tenantId() + "-billing-owner";
    }

    private String resolveCartId(String sessionId) {
        if (dbBacked()) {
            return checkoutSessionRepository.get()
                    .findMetadata(sessionId)
                    .map(CheckoutSessionRepository.SessionMetadata::cartId)
                    .filter(id -> id != null && !id.isBlank())
                    .orElse(null);
        }
        return inMemorySessionCartIds.get(sessionId);
    }

    private static PurchaseFulfillmentCommand toFulfillmentCommand(
            String orderId, String tenantId, String userId, CanonicalProduct product) {
        return new PurchaseFulfillmentCommand(
                orderId,
                tenantId,
                userId,
                product.productCode(),
                product.purchaseModeName(),
                product.lineType().name(),
                product.planKey(),
                product.tierKey(),
                product.bundleKey(),
                product.quotaProfileCode(),
                product.creditAmountMinor(),
                product.includedSeats(),
                product.seatFeatureKey(),
                30);
    }

    private void persistOrder(String orderId, CheckoutSession session, long orderValueMinor, String currencyCode) {
        if (purchaseOrderRepository.isPresent()) {
            purchaseOrderRepository.get().save(
                    orderId,
                    session.tenantId(),
                    session.checkoutSessionId(),
                    session.canonicalProductCode(),
                    "CONFIRMED",
                    orderValueMinor,
                    currencyCode);
        }
        if (checkoutSessionRepository.isPresent()) {
            checkoutSessionRepository.get().updateStatus(session.checkoutSessionId(), "COMPLETED");
        }
    }

    private void persistCancelledOrder(PurchaseOrderCreatedEvent event, CheckoutSession session) {
        if (purchaseOrderRepository.isPresent()) {
            purchaseOrderRepository.get().save(
                    event.orderId(),
                    session.tenantId(),
                    session.checkoutSessionId(),
                    event.canonicalProductCode(),
                    "CANCELLED",
                    0L,
                    null);
        }
    }

    private void completeSession(String sessionId) {
        if (!dbBacked()) {
            inMemorySessions.remove(sessionId);
            inMemorySessionUserIds.remove(sessionId);
            inMemorySessionCartIds.remove(sessionId);
        }
    }

    private void validateCheckoutRequest(CreateCheckoutSessionRequest request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (request.productCode() == null || request.productCode().isBlank()) {
            throw new IllegalArgumentException("Product code is required");
        }
        if (request.successUrl() != null && !isValidUrl(request.successUrl())) {
            throw new IllegalArgumentException("Invalid success URL format");
        }
        if (request.cancelUrl() != null && !isValidUrl(request.cancelUrl())) {
            throw new IllegalArgumentException("Invalid cancel URL format");
        }
    }

    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
