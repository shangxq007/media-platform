package com.example.platform.billing.spi;

import com.example.platform.billing.domain.BillingState;

public interface BillingEngine {
    BillingState fetchBillingState(String subjectId);
}
