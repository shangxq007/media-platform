package com.example.platform.render.api;

/** Owner-published runtime assembly. Consumers do not import Render repositories or HTTP services. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
@org.springframework.context.annotation.Import({
    com.example.platform.render.app.GraphRenderLifecycleService.class,
    com.example.platform.render.app.RenderStateTransitions.class,
    com.example.platform.render.infrastructure.RenderJobRepository.class,
    com.example.platform.render.infrastructure.GraphRenderExecutionRepository.class,
    com.example.platform.render.infrastructure.GraphAcceptedOutputQuery.class,
    com.example.platform.render.app.RenderJobStatusHistoryRepository.class,
    com.example.platform.render.app.event.RenderLifecyclePublisher.class,
    com.example.platform.render.app.event.RenderOutboxEvents.class,
    com.example.platform.entitlement.api.commercial.QuotaRuntimeConfiguration.class
})
public class GraphRenderRuntimeConfiguration {}
