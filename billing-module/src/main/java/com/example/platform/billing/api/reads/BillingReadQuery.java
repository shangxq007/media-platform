package com.example.platform.billing.api.reads;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.usage.api.*;
import java.time.Instant;
import java.util.List;
/** Read-only accounting projection. Actor must be supplied by Identity's authenticated boundary. */
public interface BillingReadQuery {
 record Usage(String recordId,UsageDimension dimension,UsageQuantity quantity,Instant recordedAt) {}
 record Summary(String tier,String currencyCode,double creditBalance) {}
 List<Usage> usage(CanonicalActor actor,String tenantId);
 Summary summary(CanonicalActor actor);
}
