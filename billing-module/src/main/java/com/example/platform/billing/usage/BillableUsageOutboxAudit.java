package com.example.platform.billing.usage;

import com.example.platform.outbox.app.OutboxEventService;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("billableUsageId", usage.billableUsageId());
        payload.put("tenantId", usage.tenantId());
        payload.put("observedUsageId", usage.observedUsageId());
        payload.put("meteringRuleId", usage.meteringRuleId());
        payload.put("meteringRuleVersion", usage.meteringRuleVersion());
        outbox.appendEvent(
                "BILLABLE_USAGE",
                usage.billableUsageId(),
                "USAGE_METERED",
                1,
                payload,
                "billable-audit:" + usage.idempotencyKey());
    }
}
