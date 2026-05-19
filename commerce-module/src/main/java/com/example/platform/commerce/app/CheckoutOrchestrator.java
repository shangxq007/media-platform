package com.example.platform.commerce.app;

import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.domain.*;
import com.example.platform.commerce.infrastructure.CheckoutSessionRepository;
import com.example.platform.commerce.infrastructure.PurchaseOrderRepository;
import com.example.platform.shared.Ids;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    private final CommerceCatalogService catalogService;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PurchaseOrderCreatedEvent> publishedEvents = new ConcurrentHashMap<>();
    private final Set<String> activeSessions = new ConcurrentSkipListSet<>();

    private final Counter sessionCreatedCounter;
    private final Counter orderConfirmedCounter;
    private final Counter orderCancelledCounter;
    private final Timer checkoutDurationTimer;
    private final Timer revenueCalculationTimer;

    public CheckoutOrchestrator(CommerceCatalogService catalogService,
                                @Autowired(required = false) CheckoutSessionRepository checkoutSessionRepository,
                                @Autowired(required = false) PurchaseOrderRepository purchaseOrderRepository,
                                MeterRegistry meterRegistry) {
        this.catalogService = catalogService;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;

        this.sessionCreatedCounter = Counter.builder("commerce.sessions.created")
                .description("Number of checkout sessions created")
                .register(meterRegistry);

        this.orderConfirmedCounter = Counter.builder("commerce.orders.confirmed")
                .description("Number of orders confirmed")
                .tag("product", "pro_monthly")
                .tag("status", "confirmed")
                .register(meterRegistry);

        this.orderCancelledCounter = Counter.builder("commerce.orders.cancelled")
                .description("Number of orders cancelled")
                .tag("status", "cancelled")
                .register(meterRegistry);

        this.checkoutDurationTimer = Timer.builder("commerce.checkout.duration")
                .description("Duration of checkout operations")
                .tags("operation", "confirmation")
                .register(meterRegistry);

        this.revenueCalculationTimer = Timer.builder("commerce.revenue.calculation")
                .description("Duration of revenue calculation operations")
                .tags("operation", "calculation")
                .register(meterRegistry);
    }

    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        validateCheckoutRequest(request);

        CanonicalProduct product = catalogService.listProducts().stream()
                .filter(p -> p.productCode().equals(request.productCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.productCode()));

        if ("subscription".equals(product.purchaseMode()) && request.successUrl() == null) {
            throw new IllegalArgumentException("Subscription products require a success URL");
        }

        CheckoutIntent intent = new CheckoutIntent(
                request.tenantId(),
                request.productCode(),
                request.purchaseMode() != null ? request.purchaseMode() : product.purchaseMode(),
                request.successUrl(),
                request.cancelUrl()
        );

        CheckoutSession session = createCheckoutSession(intent);
        activeSessions.add(session.checkoutSessionId());

        sessionCreatedCounter.increment();

        log.info("Created checkout session {} for tenant {} - product: {}",
                session.checkoutSessionId(), request.tenantId(), request.productCode());
        return new CheckoutSessionResponse(session.checkoutSessionId(), session.redirectUrl(), session.providerHint());
    }

    public CheckoutSession createCheckoutSession(CheckoutIntent intent) {
        String sessionId = Ids.newId("chk");

        validateProductAvailability(intent.canonicalProductCode(), intent.tenantId());

        CheckoutSession session = new CheckoutSession(
                sessionId,
                intent.tenantId(),
                intent.canonicalProductCode(),
                intent.successUrl(),
                "internal"
        );

        if (checkoutSessionRepository != null) {
            try {
                checkoutSessionRepository.save(session);
                log.debug("Persisted checkout session: {} for tenant: {}", sessionId, intent.tenantId());
            } catch (Exception e) {
                log.warn("Failed to persist checkout session {} for tenant {}: {}, falling back to in-memory: {}",
                        sessionId, intent.tenantId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }

        sessions.put(sessionId, session);
        activeSessions.add(sessionId);

        log.info("Created checkout session {} for tenant {}", sessionId, intent.tenantId());
        return session;
    }

    public PurchaseOrderCreatedEvent confirmCheckout(String sessionId) {
        Timer.Sample sample = Timer.start();

        try {
            CheckoutSession session = sessions.get(sessionId);
            if (session == null) {
                if (checkoutSessionRepository != null) {
                    try {
                        session = checkoutSessionRepository.findById(sessionId).orElse(null);
                    } catch (Exception e) {
                        log.warn("Failed to load checkout session from DB: {}", e.getMessage());
                    }
                }
                if (session == null) {
                    throw new IllegalArgumentException("Checkout session not found: " + sessionId);
                }
                sessions.put(sessionId, session);
            }

            String orderId = Ids.newId("ord");
            double orderValue = calculateOrderValue(session.canonicalProductCode());

            PurchaseOrderCreatedEvent event = new PurchaseOrderCreatedEvent(
                    orderId,
                    session.tenantId(),
                    session.canonicalProductCode(),
                    "CONFIRMED"
            );

            if (purchaseOrderRepository != null) {
                try {
                    purchaseOrderRepository.save(orderId, session.checkoutSessionId(),
                            session.canonicalProductCode(), "CONFIRMED", Double.valueOf(orderValue).longValue(), null);
                    log.debug("Persisted purchase order: {}", orderId);
                } catch (Exception e) {
                    log.warn("Failed to persist purchase order {}, falling back to in-memory: {}", orderId, e.getMessage());
                }
            }

            if (checkoutSessionRepository != null) {
                try {
                    checkoutSessionRepository.updateStatus(sessionId, "COMPLETED");
                } catch (Exception e) {
                    log.warn("Failed to update checkout session status for {}: {}", sessionId, e.getMessage());
                }
            }

            publishedEvents.put(orderId, event);
            activeSessions.remove(sessionId);

            orderConfirmedCounter.increment();

            log.info("Confirmed checkout session {} -> order {} (value: {})",
                    sessionId, orderId, orderValue);
            return event;

        } finally {
            sample.stop(checkoutDurationTimer);
        }
    }

    public List<PurchaseOrderCreatedEvent> getPublishedEvents() {
        return List.copyOf(publishedEvents.values());
    }

    public CheckoutSession getSession(String sessionId) {
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
        return session;
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

    private void validateProductAvailability(String productCode, String tenantId) {
        boolean isAvailable = switch (productCode) {
            case "pro_monthly", "basic_monthly" -> true;
            case "enterprise_monthly" -> "tenant-1".equals(tenantId) || "tenant-prod".equals(tenantId);
            default -> false;
        };

        if (!isAvailable) {
            throw new IllegalArgumentException("Product '" + productCode + "' is not available for this tenant");
        }
    }

    private double calculateOrderValue(String productCode) {
        return switch (productCode) {
            case "pro_monthly" -> 99.99;
            case "basic_monthly" -> 29.99;
            case "enterprise_monthly" -> 299.99;
            default -> 0.0;
        };
    }

    private boolean isValidUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
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
                    .mapToDouble(event -> switch (event.canonicalProductCode()) {
                        case "pro_monthly" -> 99.99;
                        case "basic_monthly" -> 29.99;
                        case "enterprise_monthly" -> 299.99;
                        default -> 0.0;
                    })
                    .sum();
        } finally {
            sample.stop(revenueCalculationTimer);
        }
    }

    public long getActiveSessionsCount() {
        return activeSessions.size();
    }

    public List<String> getExpiredSessions() {
        return activeSessions.stream()
                .filter(sessionId -> {
                    CheckoutSession session = sessions.get(sessionId);
                    return session != null;
                })
                .toList();
    }

    public PurchaseOrderCreatedEvent cancelCheckout(String sessionId) {
        CheckoutSession session = sessions.get(sessionId);
        if (session == null) {
            if (checkoutSessionRepository != null) {
                try {
                    session = checkoutSessionRepository.findById(sessionId).orElse(null);
                } catch (Exception e) {
                    log.warn("Failed to load checkout session from DB: {}", e.getMessage());
                }
            }
            if (session == null) {
                throw new IllegalArgumentException("Checkout session not found: " + sessionId);
            }
            sessions.put(sessionId, session);
        }

        PurchaseOrderCreatedEvent cancelledEvent = new PurchaseOrderCreatedEvent(
                Ids.newId("ord"),
                session.tenantId(),
                session.canonicalProductCode(),
                "CANCELLED"
        );

        if (purchaseOrderRepository != null) {
            try {
                purchaseOrderRepository.save(cancelledEvent.orderId(),
                        cancelledEvent.tenantId(), cancelledEvent.canonicalProductCode(),
                        "CANCELLED",
                        0L,
                        null);
                log.info("Persisted cancelled order: {}", cancelledEvent.orderId());
            } catch (Exception e) {
                log.warn("Failed to persist cancelled order {}: {}, falling back to in-memory",
                        cancelledEvent.orderId(), e.getMessage());
            }
        }

        activeSessions.remove(sessionId);
        publishedEvents.put(cancelledEvent.orderId(), cancelledEvent);

        log.info("Cancelled checkout session {} -> order {} for tenant {}",
                sessionId, cancelledEvent.orderId(), session.tenantId());
        return cancelledEvent;
    }

    public long getActiveSessionsCount(String tenantId) {
        return activeSessions.stream()
                .map(sessions::get)
                .filter(session -> session != null)
                .count();
    }
}
