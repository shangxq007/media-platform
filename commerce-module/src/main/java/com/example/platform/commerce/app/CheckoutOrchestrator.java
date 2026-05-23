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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    private final CommerceCatalogService catalogService;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Optional<PurchaseFulfillmentPort> fulfillmentPort;
    private final Optional<CheckoutPaymentPort> checkoutPaymentPort;
    private final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserIds = new ConcurrentHashMap<>();
    private final Map<String, String> sessionCartIds = new ConcurrentHashMap<>();
    private final CommerceCartService commerceCartService;
    private final Map<String, PurchaseOrderCreatedEvent> publishedEvents = new ConcurrentHashMap<>();
    private final Set<String> activeSessions = new ConcurrentSkipListSet<>();

    private final Counter sessionCreatedCounter;
    private final Counter orderConfirmedCounter;
    private final Counter orderCancelledCounter;
    private final Timer checkoutDurationTimer;
    private final Timer revenueCalculationTimer;

    public CheckoutOrchestrator(CommerceCatalogService catalogService,
                                CommerceCartService commerceCartService,
                                @Autowired(required = false) CheckoutSessionRepository checkoutSessionRepository,
                                @Autowired(required = false) PurchaseOrderRepository purchaseOrderRepository,
                                @Autowired(required = false) PurchaseFulfillmentPort fulfillmentPort,
                                @Autowired(required = false) CheckoutPaymentPort checkoutPaymentPort,
                                MeterRegistry meterRegistry) {
        this.catalogService = catalogService;
        this.commerceCartService = commerceCartService;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
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

    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        return createSession(request, null);
    }

    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request, String cartId) {
        validateCheckoutRequest(request);
        CanonicalProduct product = catalogService.requireProduct(request.productCode());

        if (product.purchaseMode() == PurchaseMode.SUBSCRIPTION && request.successUrl() == null) {
            throw new IllegalArgumentException("Subscription products require a success URL");
        }
        if (!catalogService.isAvailableForTenant(product, request.tenantId())) {
            throw new IllegalArgumentException("Product '" + request.productCode() + "' is not available for this tenant");
        }

        String purchaseMode = request.purchaseMode() != null
                ? request.purchaseMode()
                : product.purchaseModeName();

        CheckoutIntent intent = new CheckoutIntent(
                request.tenantId(),
                request.productCode(),
                purchaseMode,
                request.successUrl(),
                request.cancelUrl());

        CheckoutSession session = createCheckoutSession(intent);
        String userId = request.userId();
        if (userId != null && !userId.isBlank()) {
            sessionUserIds.put(session.checkoutSessionId(), userId);
        }
        if (cartId != null && !cartId.isBlank()) {
            sessionCartIds.put(session.checkoutSessionId(), cartId);
        }
        activeSessions.add(session.checkoutSessionId());
        sessionCreatedCounter.increment();

        long amountMinor = cartId != null ? commerceCartService.cartTotalMinor(cartId) : product.priceMinor();
        String redirectUrl = session.redirectUrl();
        String providerHint = session.providerHint();
        if (checkoutPaymentPort.isPresent()) {
            CheckoutPaymentPort.CheckoutPaymentRequest paymentRequest = new CheckoutPaymentPort.CheckoutPaymentRequest(
                    session.checkoutSessionId(),
                    request.tenantId(),
                    userId,
                    product.productCode(),
                    amountMinor,
                    product.currencyCode(),
                    request.successUrl(),
                    request.cancelUrl(),
                    cartId);
            CheckoutPaymentPort.CheckoutPaymentSession payment = checkoutPaymentPort.get()
                    .createPaymentForCheckout(paymentRequest);
            redirectUrl = payment.redirectUrl();
            providerHint = payment.providerCode() + ":" + payment.providerReference();
        }

        log.info("Created checkout session {} for tenant {} product={}",
                session.checkoutSessionId(), request.tenantId(), request.productCode());
        return new CheckoutSessionResponse(session.checkoutSessionId(), redirectUrl, providerHint);
    }

    public CheckoutSession createCheckoutSession(CheckoutIntent intent) {
        String sessionId = Ids.newId("chk");
        CanonicalProduct product = catalogService.requireProduct(intent.canonicalProductCode());
        if (!catalogService.isAvailableForTenant(product, intent.tenantId())) {
            throw new IllegalArgumentException("Product not available: " + intent.canonicalProductCode());
        }

        CheckoutSession session = new CheckoutSession(
                sessionId,
                intent.tenantId(),
                intent.canonicalProductCode(),
                intent.successUrl(),
                "internal");

        if (checkoutSessionRepository != null) {
            try {
                checkoutSessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Failed to persist checkout session {}: {}", sessionId, e.getMessage());
            }
        }
        sessions.put(sessionId, session);
        activeSessions.add(sessionId);
        return session;
    }

    public PurchaseOrderCreatedEvent confirmCheckout(String sessionId) {
        return confirmCheckout(sessionId, null);
    }

    public PurchaseOrderCreatedEvent confirmCheckout(String sessionId, String userIdOverride) {
        Timer.Sample sample = Timer.start();
        try {
            CheckoutSession session = requireSession(sessionId);
            CanonicalProduct product = catalogService.requireProduct(session.canonicalProductCode());
            String userId = resolveUserId(session, userIdOverride);

            String orderId = Ids.newId("ord");
            long orderValue = product.priceMinor();

            PurchaseOrderCreatedEvent event = new PurchaseOrderCreatedEvent(
                    orderId,
                    session.tenantId(),
                    session.canonicalProductCode(),
                    "CONFIRMED");

            persistOrder(orderId, session, orderValue);
            completeSession(sessionId);
            publishedEvents.put(orderId, event);
            activeSessions.remove(sessionId);
            orderConfirmedCounter.increment();

            fulfillmentPort.ifPresent(port -> {
                try {
                    String cartId = sessionCartIds.get(sessionId);
                    if (cartId != null) {
                        for (CanonicalProduct lineProduct : commerceCartService.resolveLines(cartId)) {
                            port.fulfill(toFulfillmentCommand(orderId, session.tenantId(), userId, lineProduct));
                        }
                        sessionCartIds.remove(sessionId);
                    } else {
                        port.fulfill(toFulfillmentCommand(orderId, session.tenantId(), userId, product));
                    }
                } catch (Exception e) {
                    log.error("Purchase fulfillment failed for order {} product {}: {}",
                            orderId, product.productCode(), e.getMessage(), e);
                    throw new IllegalStateException("Fulfillment failed for order " + orderId, e);
                }
            });

            log.info("Confirmed checkout {} -> order {} product={} valueMinor={}",
                    sessionId, orderId, product.productCode(), orderValue);
            return event;
        } finally {
            sample.stop(checkoutDurationTimer);
        }
    }

    public List<PurchaseOrderCreatedEvent> getPublishedEvents() {
        return List.copyOf(publishedEvents.values());
    }

    public CheckoutSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<CanonicalProduct> listCatalogProducts() {
        return catalogService.listProducts();
    }

    public PurchaseOrderCreatedEvent cancelCheckout(String sessionId) {
        CheckoutSession session = requireSession(sessionId);
        PurchaseOrderCreatedEvent cancelledEvent = new PurchaseOrderCreatedEvent(
                Ids.newId("ord"),
                session.tenantId(),
                session.canonicalProductCode(),
                "CANCELLED");
        persistCancelledOrder(cancelledEvent, session);
        activeSessions.remove(sessionId);
        publishedEvents.put(cancelledEvent.orderId(), cancelledEvent);
        orderCancelledCounter.increment();
        log.info("Cancelled checkout session {} for tenant {}", sessionId, session.tenantId());
        return cancelledEvent;
    }

    public List<PurchaseOrderCreatedEvent> getRecentEvents(String tenantId, int limit) {
        return publishedEvents.values().stream()
                .filter(event -> event.tenantId().equals(tenantId))
                .sorted((e1, e2) -> e2.canonicalProductCode().compareTo(e1.canonicalProductCode()))
                .limit(limit)
                .toList();
    }

    public double getTotalRevenueForTenant(String tenantId) {
        Timer.Sample sample = Timer.start();
        try {
            return publishedEvents.values().stream()
                    .filter(event -> event.tenantId().equals(tenantId))
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
        return activeSessions.size();
    }

    public long getActiveSessionsCount(String tenantId) {
        return activeSessions.stream()
                .map(sessions::get)
                .filter(session -> session != null && tenantId.equals(session.tenantId()))
                .count();
    }

    private CheckoutSession requireSession(String sessionId) {
        CheckoutSession session = sessions.get(sessionId);
        if (session == null && checkoutSessionRepository != null) {
            try {
                session = checkoutSessionRepository.findById(sessionId).orElse(null);
                if (session != null) {
                    sessions.put(sessionId, session);
                }
            } catch (Exception e) {
                log.warn("Failed to load checkout session from DB: {}", e.getMessage());
            }
        }
        if (session == null) {
            throw new IllegalArgumentException("Checkout session not found: " + sessionId);
        }
        return session;
    }

    private String resolveUserId(CheckoutSession session, String userIdOverride) {
        if (userIdOverride != null && !userIdOverride.isBlank()) {
            return userIdOverride;
        }
        String fromSession = sessionUserIds.get(session.checkoutSessionId());
        if (fromSession != null && !fromSession.isBlank()) {
            return fromSession;
        }
        return session.tenantId() + "-billing-owner";
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

    private void persistOrder(String orderId, CheckoutSession session, long orderValueMinor) {
        if (purchaseOrderRepository != null) {
            try {
                purchaseOrderRepository.save(orderId, session.checkoutSessionId(),
                        session.canonicalProductCode(), "CONFIRMED", orderValueMinor, null);
            } catch (Exception e) {
                log.warn("Failed to persist purchase order {}: {}", orderId, e.getMessage());
            }
        }
        if (checkoutSessionRepository != null) {
            try {
                checkoutSessionRepository.updateStatus(session.checkoutSessionId(), "COMPLETED");
            } catch (Exception e) {
                log.warn("Failed to update checkout session status: {}", e.getMessage());
            }
        }
    }

    private void persistCancelledOrder(PurchaseOrderCreatedEvent event, CheckoutSession session) {
        if (purchaseOrderRepository != null) {
            try {
                purchaseOrderRepository.save(
                        event.orderId(),
                        session.checkoutSessionId(),
                        event.canonicalProductCode(),
                        "CANCELLED",
                        0L,
                        null);
            } catch (Exception e) {
                log.warn("Failed to persist cancelled order: {}", e.getMessage());
            }
        }
    }

    private void completeSession(String sessionId) {
        sessionUserIds.remove(sessionId);
        sessionCartIds.remove(sessionId);
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
