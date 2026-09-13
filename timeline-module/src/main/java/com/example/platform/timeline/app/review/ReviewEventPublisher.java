package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.event.*;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.web.TenantGuard;
@org.springframework.stereotype.Service
public class ReviewEventPublisher {
 private final OutboxEventService outbox;
 public ReviewEventPublisher(OutboxEventService outbox){this.outbox=outbox;}
 public void publish(TimelineReviewCreatedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_CREATED.append(event.tenantId(),event,event.factKey()));}
 public void publish(TimelineReviewApprovedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_APPROVED.append(event.tenantId(),event,event.factKey()));}
 public void publish(TimelineReviewRejectedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_REJECTED.append(event.tenantId(),event,event.factKey()));}
 public void publish(TimelineReviewChangesRequestedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_CHANGESREQUESTED.append(event.tenantId(),event,event.factKey()));}
 public void publish(TimelineReviewCommentAddedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_COMMENTADDED.append(event.tenantId(),event,event.factKey()));}
 public void publish(TimelineReviewThreadResolvedEvent event){TenantGuard.assertSameTenant(event.tenantId());outbox.append(TimelineOutboxEvents.REVIEW_THREADRESOLVED.append(event.tenantId(),event,event.factKey()));}
}
