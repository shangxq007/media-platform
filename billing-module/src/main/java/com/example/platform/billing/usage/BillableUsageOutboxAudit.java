package com.example.platform.billing.usage;

import com.example.platform.outbox.app.OutboxEventService;


import org.springframework.stereotype.Service;

/** Durable audit/propagation adapter invoked in the metering transaction. */
@Service
public class BillableUsageOutboxAudit implements BillableUsageAuditPort {

    private final OutboxEventService outbox;

    public BillableUsageOutboxAudit(OutboxEventService outbox) {
        this.outbox = outbox;
    }

    @Override
    public void recordMetered(BillableUsage usage) {
        var payload = new BillingOutboxEvents.UsageMetered(usage.billableUsageId(), usage.tenantId(),
                usage.observedUsageId(), usage.meteringRuleId(), usage.meteringRuleVersion());
        outbox.append(BillingOutboxEvents.USAGE_METERED.append(usage.tenantId(), payload, "billable-audit:" + usage.idempotencyKey()));
    }
}
