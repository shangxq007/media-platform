package com.example.platform.entitlement.api.commercial;

/** Owner-published minimal quota runtime for non-HTTP workers. Internal persistence stays here. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
@org.springframework.context.annotation.Import({
    com.example.platform.entitlement.app.QuotaConsumptionApplicationService.class,
    com.example.platform.entitlement.app.QuotaUsageAuthority.class,
    com.example.platform.entitlement.app.QuotaPolicyService.class,
    com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository.class
})
public class QuotaRuntimeConfiguration {}
