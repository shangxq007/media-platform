package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;
import java.util.List;
@org.springframework.stereotype.Service
public class ReviewDecisionService implements ReviewDecisions {
 private final TimelineReviewRepository repo;private final TimelineReviewService reviews;
 public ReviewDecisionService(TimelineReviewRepository repo,TimelineReviewService reviews){this.repo=repo;this.reviews=reviews;}
 public List<DecisionRow> listDecisions(String id){reviews.requireReview(id,false);return repo.listDecisionsByReview(id);}
}
