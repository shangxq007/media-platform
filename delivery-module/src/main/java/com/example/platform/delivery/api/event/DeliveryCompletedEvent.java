package com.example.platform.delivery.api.event;
import com.example.platform.artifact.app.ArtifactOutputReference;
import java.time.Instant;
import java.util.Objects;
/** Accepted outcome of one actual Delivery attempt; not a Render lifecycle transition. */
public record DeliveryCompletedEvent(String deliveryJobId, ArtifactOutputReference result, String destinationId,
        int attempt, com.example.platform.delivery.domain.DeliveryProtocol protocol, String remoteUri, long bytesTransferred, Instant completedAt) {
    public DeliveryCompletedEvent {
        if(deliveryJobId==null||deliveryJobId.isBlank()||destinationId==null||destinationId.isBlank()||attempt<1)
            throw new IllegalArgumentException("Delivery identity and accepted attempt required");
        Objects.requireNonNull(result); Objects.requireNonNull(completedAt);
        Objects.requireNonNull(protocol);
        if(remoteUri==null||remoteUri.isBlank()||bytesTransferred<0)throw new IllegalArgumentException("Confirmed transfer result required");
    }
    public String tenantId(){return result.scope().tenantId();}
    public String projectId(){return result.scope().projectId();}
    public String renderJobId(){return result.scope().renderJobId();}
    public String factKey(){return "delivery-completed:"+tenantId()+":"+deliveryJobId+":"+attempt;}
}
