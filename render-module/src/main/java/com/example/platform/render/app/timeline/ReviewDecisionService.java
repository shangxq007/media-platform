package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.ReviewDecision;
import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewDecisionService {

    private final TimelineReviewRepository reviewRepository;

    public ReviewDecisionService(TimelineReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewDecision recordDecision(String reviewId, String reviewerUserId,
                                           ReviewDecision.Decision decision) {
        String decisionId = Ids.newId("rdec");
        OffsetDateTime now = OffsetDateTime.now();

        reviewRepository.insertDecision(decisionId, reviewId, reviewerUserId,
                decision.name(), now);

        return switch (decision) {
            case APPROVE -> ReviewDecision.approve(decisionId, reviewId, reviewerUserId);
            case REQUEST_CHANGES -> ReviewDecision.requestChanges(decisionId, reviewId, reviewerUserId);
            case REJECT -> ReviewDecision.reject(decisionId, reviewId, reviewerUserId);
        };
    }

    public List<TimelineReviewRepository.DecisionRow> listDecisions(String reviewId) {
        return reviewRepository.listDecisionsByReview(reviewId);
    }
}
