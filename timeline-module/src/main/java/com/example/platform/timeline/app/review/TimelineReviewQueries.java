package com.example.platform.timeline.app.review;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;
import com.example.platform.shared.web.TenantGuard;
import java.util.*;
@org.springframework.stereotype.Service
public class TimelineReviewQueries implements ReviewQueries {
 private final TimelineReviewRepository repo;private final ReviewAuthorization auth;
 public TimelineReviewQueries(TimelineReviewRepository repo,ReviewAuthorization auth){this.repo=repo;this.auth=auth;}
 public Optional<ReviewRow> findOwnedById(String id,String project,String tenant){TenantGuard.assertSameTenant(tenant);auth.require(project,false);return repo.findOwnedById(id,project,tenant);}
 public Optional<ReviewRow> findByTargetId(String target){var row=repo.findByTargetId(target);row.ifPresent(r->auth.require(r.projectId(),false));return row;}
 public List<ReviewRow> listOwnedByProject(String project,String tenant,int limit){TenantGuard.assertSameTenant(tenant);auth.require(project,false);return repo.listOwnedByProject(project,tenant,Math.max(1,limit));}
}
