package com.example.platform.production;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator that runs the egress proxy smoke test.
 *
 * <p>Only registered when {@code egress.proxy.smoke.enabled=true}.
 * By default, NOT included in the readiness group — set
 * {@code egress.proxy.smoke.include-in-readiness=true} to include it.
 *
 * <p>WARNING: Including in readiness means external dependency downtime
 * will cause Pods to be removed from Service load balancers.
 */
@Component("egressProxySmoke")
@ConditionalOnProperty(prefix = "egress.proxy.smoke", name = "enabled", havingValue = "true")
public class EgressProxySmokeHealthIndicator implements HealthIndicator {

    private final EgressProxySmokeService smokeService;

    public EgressProxySmokeHealthIndicator(EgressProxySmokeService smokeService) {
        this.smokeService = smokeService;
    }

    @Override
    public Health health() {
        EgressProxySmokeService.SmokeResult result = smokeService.execute();

        return switch (result.status()) {
            case SUCCESS -> Health.up()
                    .withDetails(result.toHealthDetails())
                    .build();
            case DISABLED -> Health.up()
                    .withDetail("status", "DISABLED")
                    .build();
            case CONFIG_ERROR -> Health.down()
                    .withDetails(result.toHealthDetails())
                    .build();
            case FAILED -> Health.down()
                    .withDetails(result.toHealthDetails())
                    .build();
        };
    }
}
