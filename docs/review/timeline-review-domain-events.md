# Timeline review and revision events

Timeline owns these typed facts and their `TimelineOutboxEvents` catalog. Outbox owns the existing versioned envelope, codec and dispatcher. There are no shared-package aliases or generic targetType-discriminated review facts.

| Durable contract | Version | Actual producer | Actual consumers |
|---|---:|---|---|
| timeline.review.created | 1 | TimelineReviewService, accepted creation transaction | Intentional publication contract (CAR-0093/0095); no internal consumer |
| timeline.review.approved | 1 | TimelineReviewService, accepted status + decision transaction | Audit, Notification |
| timeline.review.rejected | 1 | TimelineReviewService, accepted status + decision transaction | Audit, Notification |
| timeline.review.changes_requested | 1 | TimelineReviewService, accepted status + decision transaction | Audit, Notification |
| timeline.review.comment.added | 1 | TimelineCommentService, accepted comment transaction | Audit, Notification |
| timeline.review.thread.resolved | 1 | TimelineCommentService, accepted resolution transaction | Audit, Notification |
| timeline.merged | 2 | TimelineRevisionSaveService, same explicit jOOQ transaction as accepted merge | Audit, Notification |
| timeline.restored | 2 | TimelineRevisionSaveService, same explicit jOOQ transaction as verified restore | Audit, Notification |
| timeline.revision.created | 2 | No current producer; intentional published contract retained under CAR-0096 | No current consumer |

Contracts live in `timeline.api.event`. Review references bind review identity to a scoped Timeline revision. Merge/restore facts carry scoped accepted result/source identities. Decision/comment IDs or a unique accepted resolution fact ID distinguish transitions. Facts contain actual server-resolved actor IDs; they do not cause another lifecycle or infer state from diagnostics.

Review and revision mutations roll back with failed append. Existing merge duplicate handling avoids a new event for an already accepted result. Audit and Notification use stable fact identities for local duplicate handling. Existing notification subscription topics are preserved as Notification-owned ingress projections; they are not compatibility registrations for retired domain payloads.

The six old generic review keys and all nine shared Java definitions are retired. Prior version-1 Timeline revision payloads are incompatible with the new typed scope and are explicitly rejected/dead-lettered. No translator, dual publication, field default or historical rewrite is supplied. Valid typed inputs use the existing production envelope and codec.

Asset/Marketplace publication events and their remaining authority convergence are outside this scope (EP29C). The contracts do not add frontend pages, a general Project Open flow or real provider execution. Notification delivery does not claim exactly-once external effects.
