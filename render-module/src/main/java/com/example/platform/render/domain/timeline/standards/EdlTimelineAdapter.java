package com.example.platform.render.domain.timeline.standards;

import com.example.platform.render.domain.timeline.TimelineAssetRef;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CMX3600 EDL import skeleton (粗剪交接).
 */
public final class EdlTimelineAdapter {

    private static final Pattern EVENT_LINE = Pattern.compile(
            "\\d{3,6}\\s+\\S+\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)");

    private EdlTimelineAdapter() {
    }

    public static TimelineSpec parse(String edlContent, String defaultMediaUri) {
        List<TimelineClip> clips = new ArrayList<>();
        double timelinePos = 0;
        int index = 0;

        for (String line : edlContent.split("\n")) {
            Matcher m = EVENT_LINE.matcher(line.trim());
            if (!m.find()) {
                continue;
            }
            String reel = m.group(1);
            String srcIn = m.group(4);
            String srcOut = m.group(5);
            double inSec = timecodeToSeconds(srcIn);
            double outSec = timecodeToSeconds(srcOut);
            double duration = Math.max(0.1, outSec - inSec);
            String uri = defaultMediaUri != null ? defaultMediaUri : "edl-reel://" + reel;
            clips.add(TimelineClip.of(
                    "edl-" + (++index),
                    TimelineAssetRef.of("edl-asset-" + index, uri),
                    timelinePos,
                    inSec,
                    outSec));
            timelinePos += duration;
        }

        TimelineTrack video = new TimelineTrack("edl-v1", "EDL Video", TimelineTrack.TrackType.VIDEO,
                0, clips, false, false);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("format", "mp4");
        meta.put("platform.import.source", "edl");
        meta.put("platform.otio.exportLossy", "true");

        return new TimelineSpec(
                "edl-import",
                "EDL Import",
                "Imported from CMX EDL",
                List.of(video),
                List.of(),
                TimelineOutputSpec.mp4_1080p30(),
                timelinePos,
                meta);
    }

    private static double timecodeToSeconds(String tc) {
        if (tc == null || tc.isBlank()) {
            return 0;
        }
        String[] parts = tc.split("[:;]");
        if (parts.length < 4) {
            return 0;
        }
        return Integer.parseInt(parts[0]) * 3600.0
                + Integer.parseInt(parts[1]) * 60.0
                + Integer.parseInt(parts[2])
                + Integer.parseInt(parts[3]) / 24.0;
    }
}
