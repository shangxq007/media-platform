package com.example.platform.timeline.app.review;
import com.example.platform.shared.events.*;
import com.example.platform.timeline.api.review.TimelineReviewOutboxEvents;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.web.TenantGuard;
@org.springframework.stereotype.Service
public class ReviewEventPublisher {
private final OutboxEventService outbox;
public ReviewEventPublisher(OutboxEventService outbox){this.outbox=outbox;}
public void publish(ReviewCreatedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWCREATEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
public void publish(ReviewApprovedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWAPPROVEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
public void publish(ReviewRejectedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWREJECTEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
public void publish(ReviewChangesRequestedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWCHANGESREQUESTEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
public void publish(ReviewCommentAddedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWCOMMENTADDEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
public void publish(ReviewThreadResolvedEvent e){outbox.append(TimelineReviewOutboxEvents.REVIEWTHREADRESOLVEDEVENT.append(TenantGuard.requireTenantId(),e,null));}
}
