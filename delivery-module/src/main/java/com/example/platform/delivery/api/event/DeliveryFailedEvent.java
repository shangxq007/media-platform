package com.example.platform.delivery.api.event;
import com.example.platform.artifact.app.ArtifactOutputReference;
import java.time.Instant;
import java.util.Objects;
/** Accepted outcome of one actual Delivery attempt; not a Render lifecycle transition. */
public record DeliveryFailedEvent(String deliveryJobId, ArtifactOutputReference result, String destinationId,
        int attempt, String errorCode, String errorMessage, Instant failedAt) {
    public DeliveryFailedEvent {
        if(deliveryJobId==null||deliveryJobId.isBlank()||destinationId==null||destinationId.isBlank()||attempt<1)
            throw new IllegalArgumentException("Delivery identity and accepted attempt required");
        Objects.requireNonNull(result); Objects.requireNonNull(failedAt);
        if(errorCode==null||errorCode.isBlank())throw new IllegalArgumentException("Failure code required");
    }
    public String tenantId(){return result.scope().tenantId();}
    public String projectId(){return result.scope().projectId();}
    public String renderJobId(){return result.scope().renderJobId();}
    public String factKey(){return "delivery-failed:"+tenantId()+":"+deliveryJobId+":"+attempt;}
}
