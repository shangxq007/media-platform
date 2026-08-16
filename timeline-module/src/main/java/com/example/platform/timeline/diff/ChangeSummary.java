package com.example.platform.timeline.diff;

/**
 * Deterministic summary of changes between two Timeline documents.
 */
public final class ChangeSummary {

    private final int total;
    private final int tracksAdded;
    private final int tracksRemoved;
    private final int tracksChanged;
    private final int tracksReordered;
    private final int clipsAdded;
    private final int clipsRemoved;
    private final int clipsChanged;
    private final int clipsMoved;
    private final int clipsReordered;

    public ChangeSummary(
            int total,
            int tracksAdded,
            int tracksRemoved,
            int tracksChanged,
            int tracksReordered,
            int clipsAdded,
            int clipsRemoved,
            int clipsChanged,
            int clipsMoved,
            int clipsReordered) {
        this.total = total;
        this.tracksAdded = tracksAdded;
        this.tracksRemoved = tracksRemoved;
        this.tracksChanged = tracksChanged;
        this.tracksReordered = tracksReordered;
        this.clipsAdded = clipsAdded;
        this.clipsRemoved = clipsRemoved;
        this.clipsChanged = clipsChanged;
        this.clipsMoved = clipsMoved;
        this.clipsReordered = clipsReordered;
    }

    public int getTotal() { return total; }
    public int getTracksAdded() { return tracksAdded; }
    public int getTracksRemoved() { return tracksRemoved; }
    public int getTracksChanged() { return tracksChanged; }
    public int getTracksReordered() { return tracksReordered; }
    public int getClipsAdded() { return clipsAdded; }
    public int getClipsRemoved() { return clipsRemoved; }
    public int getClipsChanged() { return clipsChanged; }
    public int getClipsMoved() { return clipsMoved; }
    public int getClipsReordered() { return clipsReordered; }

    public static ChangeSummary empty() {
        return new ChangeSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static ChangeSummary compute(java.util.List<TimelineChange> changes) {
        int tracksAdded = 0, tracksRemoved = 0, tracksChanged = 0, tracksReordered = 0;
        int clipsAdded = 0, clipsRemoved = 0, clipsChanged = 0, clipsMoved = 0, clipsReordered = 0;

        for (TimelineChange c : changes) {
            switch (c.getChangeType()) {
                case TRACK_ADDED -> tracksAdded++;
                case TRACK_REMOVED -> tracksRemoved++;
                case TRACK_PROPERTY_CHANGED -> tracksChanged++;
                case TRACK_REORDERED -> tracksReordered++;
                case CLIP_ADDED -> clipsAdded++;
                case CLIP_REMOVED -> clipsRemoved++;
                case CLIP_PROPERTY_CHANGED -> clipsChanged++;
                case CLIP_MOVED -> clipsMoved++;
                case CLIP_REORDERED -> clipsReordered++;
                default -> { /* counted in total */ }
            }
        }

        int total = changes.size();
        return new ChangeSummary(total, tracksAdded, tracksRemoved, tracksChanged, tracksReordered,
                clipsAdded, clipsRemoved, clipsChanged, clipsMoved, clipsReordered);
    }
}
