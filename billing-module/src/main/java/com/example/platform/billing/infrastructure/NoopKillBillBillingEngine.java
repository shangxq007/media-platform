package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.BillingState;
import com.example.platform.billing.spi.BillingEngine;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NoopKillBillBillingEngine implements BillingEngine {
    @Override
    public BillingState fetchBillingState(String subjectId) {
        return new BillingState(subjectId, "projected", Instant.now().plusSeconds(86400), "killbill-demo");
    }
}
