package com.example.platform.timeline.api.event;
import com.example.platform.outbox.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public class TimelineOutboxEvents implements OutboxEventCatalog {
 public static final OutboxEventType<TimelineReviewCreatedEvent> REVIEW_CREATED=new OutboxEventType<>("timeline.review.created",1,"timeline_review",TimelineReviewCreatedEvent.class,TimelineReviewCreatedEvent::reviewId,TimelineReviewCreatedEvent::tenantId);
 public static final OutboxEventType<TimelineReviewApprovedEvent> REVIEW_APPROVED=new OutboxEventType<>("timeline.review.approved",1,"timeline_review",TimelineReviewApprovedEvent.class,TimelineReviewApprovedEvent::reviewId,TimelineReviewApprovedEvent::tenantId);
 public static final OutboxEventType<TimelineReviewRejectedEvent> REVIEW_REJECTED=new OutboxEventType<>("timeline.review.rejected",1,"timeline_review",TimelineReviewRejectedEvent.class,TimelineReviewRejectedEvent::reviewId,TimelineReviewRejectedEvent::tenantId);
 public static final OutboxEventType<TimelineReviewChangesRequestedEvent> REVIEW_CHANGESREQUESTED=new OutboxEventType<>("timeline.review.changes_requested",1,"timeline_review",TimelineReviewChangesRequestedEvent.class,TimelineReviewChangesRequestedEvent::reviewId,TimelineReviewChangesRequestedEvent::tenantId);
 public static final OutboxEventType<TimelineReviewCommentAddedEvent> REVIEW_COMMENTADDED=new OutboxEventType<>("timeline.review.comment.added",1,"timeline_review",TimelineReviewCommentAddedEvent.class,TimelineReviewCommentAddedEvent::commentId,TimelineReviewCommentAddedEvent::tenantId);
 public static final OutboxEventType<TimelineReviewThreadResolvedEvent> REVIEW_THREADRESOLVED=new OutboxEventType<>("timeline.review.thread.resolved",1,"timeline_review",TimelineReviewThreadResolvedEvent.class,TimelineReviewThreadResolvedEvent::threadId,TimelineReviewThreadResolvedEvent::tenantId);
 public static final OutboxEventType<TimelineMergedEvent> MERGED=new OutboxEventType<>("timeline.merged",2,"timeline_revision",TimelineMergedEvent.class,TimelineMergedEvent::mergeRevisionId,TimelineMergedEvent::tenantId);
 public static final OutboxEventType<TimelineRestoredEvent> RESTORED=new OutboxEventType<>("timeline.restored",2,"timeline_revision",TimelineRestoredEvent.class,TimelineRestoredEvent::newRevisionId,TimelineRestoredEvent::tenantId);
 public static final OutboxEventType<TimelineRevisionCreatedEvent> REVISION_CREATED=new OutboxEventType<>("timeline.revision.created",2,"timeline_revision",TimelineRevisionCreatedEvent.class,TimelineRevisionCreatedEvent::revisionId,TimelineRevisionCreatedEvent::tenantId);
public List<OutboxEventType<?>> types(){return List.of(REVIEW_CREATED,REVIEW_APPROVED,REVIEW_REJECTED,REVIEW_CHANGESREQUESTED,REVIEW_COMMENTADDED,REVIEW_THREADRESOLVED,MERGED,RESTORED,REVISION_CREATED);}
}
