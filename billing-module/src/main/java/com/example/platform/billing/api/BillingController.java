package com.example.platform.billing.api;

import com.example.platform.billing.app.BillingProjectionService;
import com.example.platform.billing.domain.BillingState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingProjectionService billingProjectionService;

    public BillingController(BillingProjectionService billingProjectionService) {
        this.billingProjectionService = billingProjectionService;
    }

    @GetMapping("/subjects/{subjectId}")
    public BillingState current(@PathVariable String subjectId) {
        return billingProjectionService.currentState(subjectId);
    }
}
