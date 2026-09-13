package com.example.platform.timeline.api.review;
import com.example.platform.shared.events.*;
import com.example.platform.outbox.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public class TimelineReviewOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<ReviewCreatedEvent> REVIEWCREATEDEVENT = new OutboxEventType<>("review.created", 1, "REVIEW", ReviewCreatedEvent.class, ReviewCreatedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewApprovedEvent> REVIEWAPPROVEDEVENT = new OutboxEventType<>("review.approved", 1, "REVIEW", ReviewApprovedEvent.class, ReviewApprovedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewRejectedEvent> REVIEWREJECTEDEVENT = new OutboxEventType<>("review.rejected", 1, "REVIEW", ReviewRejectedEvent.class, ReviewRejectedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewChangesRequestedEvent> REVIEWCHANGESREQUESTEDEVENT = new OutboxEventType<>("review.changes_requested", 1, "REVIEW", ReviewChangesRequestedEvent.class, ReviewChangesRequestedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewCommentAddedEvent> REVIEWCOMMENTADDEDEVENT = new OutboxEventType<>("review.comment.added", 1, "REVIEW", ReviewCommentAddedEvent.class, ReviewCommentAddedEvent::commentId, event -> null);
    public static final OutboxEventType<ReviewThreadResolvedEvent> REVIEWTHREADRESOLVEDEVENT = new OutboxEventType<>("review.thread.resolved", 1, "REVIEW", ReviewThreadResolvedEvent.class, ReviewThreadResolvedEvent::threadId, event -> null);
public List<OutboxEventType<?>> types(){return List.of(REVIEWCREATEDEVENT,REVIEWAPPROVEDEVENT,REVIEWREJECTEDEVENT,REVIEWCHANGESREQUESTEDEVENT,REVIEWCOMMENTADDEDEVENT,REVIEWTHREADRESOLVEDEVENT);}
}
