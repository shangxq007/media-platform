package com.example.platform.timeline.api.review;
import com.example.platform.timeline.diff.merge.*;
import com.example.platform.timeline.api.review.ReviewRecords.*;
import java.util.*;
public interface TimelineComments {
 TimelineComment addComment(String review,String revision,String thread,EntityRef ref,String author,String content);
 CommentRow addFrameComment(String project, String review, String revision, String author, String content,
                           ReviewFrameAnchor anchor, UUID requestId);
 List<CommentRow> listProjectComments(String project);
 Optional<CommentRow> findFrameComment(String project, String commentId);
 Optional<FrameCommentTransition> transitionFrameComment(String project, String commentId,
                                                        FrameCommentStatus status, String expectedVersion);
 List<CommentRow> listComments(String review);
 boolean resolveThread(String review,String thread);
 boolean reopenThread(String review,String thread);
 List<ThreadRow> listThreads(String review);
}
