package com.example.platform.timeline.api.revision;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.TimelineResolutionIntent;
import java.util.Map;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineMergeOperations {
TimelineMergeResult merge(TimelineMergeRequest request);

TimelineMergeResult mergeSemantic(TimelineMergeRequest request);

TimelineMergeResult merge(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions);
}
