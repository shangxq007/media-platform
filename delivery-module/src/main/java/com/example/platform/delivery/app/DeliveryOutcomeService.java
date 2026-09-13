package com.example.platform.delivery.app;
import com.example.platform.delivery.api.event.*;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.web.TenantGuard;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;

/** The local outcome and its durable fact commit together, after external transport. */
@Service
public class DeliveryOutcomeService {
    private final DSLContext dsl;
    private final OutboxEventService outbox;
    public DeliveryOutcomeService(DSLContext dsl,OutboxEventService outbox){this.dsl=dsl;this.outbox=outbox;}
    @Transactional
    public void completed(DeliveryCompletedEvent event){
        TenantGuard.assertSameTenant(event.tenantId());
        int changed=dsl.update(DELIVERY_JOB).set(DELIVERY_JOB.STATUS,"COMPLETED")
            .set(DELIVERY_JOB.REMOTE_URI,event.remoteUri()).set(DELIVERY_JOB.BYTES_TRANSFERRED,event.bytesTransferred())
            .set(DELIVERY_JOB.COMPLETED_AT,LocalDateTime.ofInstant(event.completedAt(),ZoneOffset.UTC))
            .where(DELIVERY_JOB.ID.eq(event.deliveryJobId())).and(DELIVERY_JOB.TENANT_ID.eq(event.tenantId()))
            .and(DELIVERY_JOB.PROJECT_ID.eq(event.projectId())).and(DELIVERY_JOB.RENDER_JOB_ID.eq(event.renderJobId()))
            .and(DELIVERY_JOB.ARTIFACT_ID.eq(event.result().artifactId().value())).and(DELIVERY_JOB.DESTINATION_ID.eq(event.destinationId()))
            .and(DELIVERY_JOB.STATUS.eq("RUNNING")).and(DELIVERY_JOB.ATTEMPT_COUNT.eq(event.attempt())).execute();
        if(changed!=1)throw new IllegalStateException("Stale or mismatched Delivery completion");
        outbox.append(DeliveryOutboxEvents.COMPLETED.append(event.tenantId(),event,event.factKey()));
    }
    @Transactional
    public void failed(DeliveryFailedEvent event){
        TenantGuard.assertSameTenant(event.tenantId());
        String message=event.errorMessage();
        int changed=dsl.update(DELIVERY_JOB).set(DELIVERY_JOB.STATUS,"FAILED")
            .set(DELIVERY_JOB.ERROR_CODE,event.errorCode()).set(DELIVERY_JOB.ERROR_MESSAGE,message!=null&&message.length()>2000?message.substring(0,2000):message)
            .set(DELIVERY_JOB.COMPLETED_AT,LocalDateTime.ofInstant(event.failedAt(),ZoneOffset.UTC))
            .where(DELIVERY_JOB.ID.eq(event.deliveryJobId())).and(DELIVERY_JOB.TENANT_ID.eq(event.tenantId()))
            .and(DELIVERY_JOB.PROJECT_ID.eq(event.projectId())).and(DELIVERY_JOB.RENDER_JOB_ID.eq(event.renderJobId()))
            .and(DELIVERY_JOB.ARTIFACT_ID.eq(event.result().artifactId().value())).and(DELIVERY_JOB.DESTINATION_ID.eq(event.destinationId()))
            .and(DELIVERY_JOB.STATUS.eq("RUNNING")).and(DELIVERY_JOB.ATTEMPT_COUNT.eq(event.attempt())).execute();
        if(changed!=1)throw new IllegalStateException("Stale or mismatched Delivery failure");
        outbox.append(DeliveryOutboxEvents.FAILED.append(event.tenantId(),event,event.factKey()));
    }
}
