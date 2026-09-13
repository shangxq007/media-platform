package com.example.platform.timeline.api.composition;
import com.example.platform.timeline.api.composition.TimelineImportRequest;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineImport {
String importTimeline(TimelineImportRequest request);
}
