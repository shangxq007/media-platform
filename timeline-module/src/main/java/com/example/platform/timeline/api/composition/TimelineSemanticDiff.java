package com.example.platform.timeline.api.composition;
import com.example.platform.timeline.diff.merge.SemanticDiffResult;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineSemanticDiff {
SemanticDiffResult diff(String oldJson, String newJson) throws java.io.IOException;
}
