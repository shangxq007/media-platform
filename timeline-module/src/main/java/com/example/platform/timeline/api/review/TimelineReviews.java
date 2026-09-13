package com.example.platform.timeline.api.review;
import com.example.platform.timeline.diff.merge.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import java.util.*;
/** Review commands have no request identity. On nonterminal reviews, repeating the
 * authenticated actor's latest decision with the same aggregate outcome is a no-op.
 * A different actor decision or changed aggregate outcome records a new decision.
 * This is current-decision idempotency, not idempotency for arbitrary delayed requests
 * across intervening state/actor-decision changes. Closed/merged reviews reject actions. */
public interface TimelineReviews {
 TimelineReview createReview(String project,String revision,String author,String title,String description);
 ReviewRow createAssetReview(String project,String asset,String author,String title,String description);
 Optional<ReviewRow> getReview(String project,String tenant,String id);
 List<ReviewRow> listReviews(String project,String tenant,int limit);
 void approve(String id,String actor);
 void requestChanges(String id,String actor);
 void reject(String id);
 MergeGuardResult checkMergeGuard(String id);
    public record MergeGuardResult(boolean canMerge, String reason) {
        public static MergeGuardResult allowed() {
            return new MergeGuardResult(true, null);
        }
        public static MergeGuardResult blocked(String reason) {
            return new MergeGuardResult(false, reason);
        }
    }
}
