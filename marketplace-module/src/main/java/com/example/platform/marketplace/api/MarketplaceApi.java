package com.example.platform.marketplace.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Marketplace commands resolve their authenticated actor and resource scope on the server. */
public interface MarketplaceApi {
    enum Status { DRAFT, READY, PUBLISHED, ARCHIVED }
    enum ReviewStatus { OPEN, APPROVED, CHANGES_REQUESTED, REJECTED }
    enum Decision { APPROVE, REQUEST_CHANGES, REJECT }
    enum Transition { PUBLISH, ARCHIVE }
    record Create(String commandId, MarketplacePublicationSubjectRef subject, String title, String summary, String description) {}
    record Edit(String commandId, long expectedVersion, String title, String summary, String description) {}
    record Submit(String commandId, long expectedVersion, String title, String description) {}
    record Decide(String commandId, long expectedVersion, Decision decision) {}
    record Change(String commandId, long expectedVersion, Transition transition) {}
    record Comment(String commandId, long expectedVersion, String threadId, String content) {}
    record Resolve(String commandId, long expectedVersion, String threadId) {}
    record Listing(String id, MarketplacePublicationSubjectRef subject, String tenantId, String workspaceId,
                   String projectId, String title, String summary, String description, Status status,
                   long version, String reviewId, String createdBy, Instant createdAt, Instant updatedAt) {}
    record Review(String id, String listingId, MarketplacePublicationSubjectRef subject, ReviewStatus status,
                  String authorId, String title, String description, long version, List<ReviewComment> comments) {}
    record ReviewComment(String id, String threadId, String authorId, String content, boolean resolved, Instant createdAt) {}
    /** Public metadata only. No private review, actor/membership, storage path or download grant. */
    record PublicListing(String id, MarketplacePublicationSubjectRef subject, String title, String summary,
                         String description, String listingType, long version, Instant publishedAt) {}
    record SearchResult(int total, int offset, int limit, List<PublicListing> results) {}
    /** Trusted owner fact query for downstream projections; not a permission grant. */
    record PublicationFact(String listingId, long version, Status status, MarketplacePublicationSubjectRef subject) {}

    record ProjectSummary(int total, int published) {}
    ProjectSummary summary(String projectId);
    Listing create(String projectId, String expectedWorkspaceId, Create command);
    Listing edit(String projectId, String listingId, Edit command);
    Review submit(String projectId, String listingId, Submit command);
    Review decide(String projectId, String reviewId, Decide command);
    Listing transition(String projectId, String listingId, Change command);
    Review comment(String projectId, String reviewId, Comment command);
    Review resolve(String projectId, String reviewId, Resolve command);
    boolean canPublish(String projectId, String listingId);
    Listing managedListing(String projectId, String listingId);
    Optional<Listing> managedByAsset(String assetId);
    List<Listing> managedByProject(String projectId, int limit);
    Review review(String projectId, String reviewId);
    SearchResult discover(String query, String workspaceId, int offset, int limit);
    Optional<PublicListing> publicListing(String listingId);
    Optional<PublicationFact> publicationFact(String tenantId, String projectId, String assetId);
}
