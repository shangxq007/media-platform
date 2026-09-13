package com.example.platform.timeline.api.review;
import com.example.platform.timeline.diff.merge.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import java.util.*;
public interface ReviewQueries {
 Optional<ReviewRow> findOwnedById(String id,String project,String tenant);
 Optional<ReviewRow> findByTargetId(String target);
 List<ReviewRow> listOwnedByProject(String project,String tenant,int limit);
}
