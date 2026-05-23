package com.example.platform.ai.domain;

public interface ModelRouter {

    RoutePlan routePlan(String capability);

    default String route(String capability) {
        return routePlan(capability).primary().providerId();
    }
}