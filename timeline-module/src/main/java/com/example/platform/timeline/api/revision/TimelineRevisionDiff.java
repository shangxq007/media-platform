package com.example.platform.timeline.api.revision;
import java.util.List;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineRevisionDiff {
ChangeSummary summarize(String parentInternalJson, String newInternalJson);

String summarizeJson(String parentInternalJson, String newInternalJson);

DetailedCompare compare(String fromInternalJson, String toInternalJson);

public record ChangeSummary(
            boolean supported,
            int tracksAdded,
            int tracksRemoved,
            int tracksModified,
            int clipsAdded,
            int clipsRemoved,
            int clipsModified,
            int assetsAdded,
            int assetsRemoved,
            int parentInternalRevision,
            int currentInternalRevision) {

        public static ChangeSummary unsupported() {
            return new ChangeSummary(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public int totalTrackChanges() {
            return tracksAdded + tracksRemoved + tracksModified;
        }

        public int totalClipChanges() {
            return clipsAdded + clipsRemoved + clipsModified;
        }
    }

public record EntityChange(String kind, String entityId, String action) {}

public record DetailedCompare(boolean supported, ChangeSummary summary, List<EntityChange> entities) {}
}
