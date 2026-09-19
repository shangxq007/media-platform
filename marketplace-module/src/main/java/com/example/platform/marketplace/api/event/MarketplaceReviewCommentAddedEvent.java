package com.example.platform.marketplace.api.event;
public record MarketplaceReviewCommentAddedEvent(MarketplaceEventReference reference, String reviewId, String threadId, String commentId) {
    public MarketplaceReviewCommentAddedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);MarketplaceEventReference.require(threadId);MarketplaceEventReference.require(commentId);}
    public String factKey(){return reference.factKey();}
}
