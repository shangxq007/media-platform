package com.example.platform.security;

import com.example.platform.security.PhaseZeroContainmentPolicy.Classification;
import com.example.platform.security.RuntimeMvcRouteDiscovery.RuntimeRoute;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Fails startup if the live application route universe is empty or not completely classified. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class RuntimeRoutePolicyVerifier implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RuntimeRoutePolicyVerifier.class);

    private final RuntimeMvcRouteDiscovery routeDiscovery;

    public RuntimeRoutePolicyVerifier(RuntimeMvcRouteDiscovery routeDiscovery) {
        this.routeDiscovery = routeDiscovery;
    }

    @Override
    public void afterSingletonsInstantiated() {
        VerificationReport report = verify();
        log.info("Runtime MVC route policy verified: routes={}, classifications={}",
                report.routeCount(), report.classificationCounts());
    }

    public VerificationReport verify() {
        List<RuntimeRoute> routes = routeDiscovery.discoverApplicationRoutes();
        if (routes.isEmpty()) {
            throw new RoutePolicyVerificationException(
                    "Spring MVC discovered no com.example.platform application routes; "
                            + "an empty route universe cannot establish containment completeness");
        }

        EnumMap<Classification, Long> counts = new EnumMap<>(Classification.class);
        for (Classification classification : Classification.values()) {
            counts.put(classification, 0L);
        }
        List<RuntimeRoute> unclassified = new ArrayList<>();
        for (RuntimeRoute route : routes) {
            PhaseZeroContainmentPolicy.classify(route.method(), route.path())
                    .ifPresentOrElse(
                            classification -> counts.merge(classification, 1L, Long::sum),
                            () -> unclassified.add(route));
        }
        if (!unclassified.isEmpty()) {
            String details = unclassified.stream()
                    .map(RuntimeRoute::displayName)
                    .collect(Collectors.joining(System.lineSeparator()));
            throw new RoutePolicyVerificationException(
                    "Unclassified Spring MVC application routes:" + System.lineSeparator() + details);
        }
        return new VerificationReport(routes.size(), counts, routes);
    }

    public record VerificationReport(
            int routeCount,
            Map<Classification, Long> classificationCounts,
            List<RuntimeRoute> routes) {

        public VerificationReport {
            classificationCounts = Map.copyOf(classificationCounts);
            routes = List.copyOf(routes);
        }
    }

    public static final class RoutePolicyVerificationException extends IllegalStateException {
        public RoutePolicyVerificationException(String message) {
            super(message);
        }
    }
}
