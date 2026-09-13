package com.example.platform.timeline.api.review;
import com.example.platform.timeline.diff.merge.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import java.util.*;
public interface TimelineComments {
 TimelineComment addComment(String review,String revision,String thread,EntityRef ref,String author,String content);
 List<CommentRow> listComments(String review);
 boolean resolveThread(String review,String thread);
 boolean reopenThread(String review,String thread);
 List<ThreadRow> listThreads(String review);
}
