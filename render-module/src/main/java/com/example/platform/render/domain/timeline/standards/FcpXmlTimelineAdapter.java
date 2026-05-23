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
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * FCPXML import skeleton (Final Cut Pro 交换).
 */
public final class FcpXmlTimelineAdapter {

    private FcpXmlTimelineAdapter() {
    }

    public static TimelineSpec parse(String fcpXml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(fcpXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            List<TimelineClip> clips = new ArrayList<>();
            NodeList assets = doc.getElementsByTagName("asset");
            Map<String, String> assetUris = new LinkedHashMap<>();
            for (int i = 0; i < assets.getLength(); i++) {
                Element asset = (Element) assets.item(i);
                String id = asset.getAttribute("id");
                String src = asset.getAttribute("src");
                if (!id.isBlank() && !src.isBlank()) {
                    assetUris.put(id, src);
                }
            }

            NodeList spineClips = doc.getElementsByTagName("clip");
            double timelineStart = 0;
            int index = 0;
            for (int i = 0; i < spineClips.getLength(); i++) {
                Element clip = (Element) spineClips.item(i);
                String ref = clip.getAttribute("ref");
                String durationStr = clip.getAttribute("duration");
                double duration = parseFcpTime(durationStr, 5.0);
                String uri = assetUris.getOrDefault(ref, "fcpxml://unknown/" + ref);
                clips.add(TimelineClip.of(
                        "fcpx-" + (++index),
                        TimelineAssetRef.of("fcpx-asset-" + index, uri),
                        timelineStart,
                        0,
                        duration));
                timelineStart += duration;
            }

            TimelineTrack video = new TimelineTrack("fcpx-v1", "FCPXML Video",
                    TimelineTrack.TrackType.VIDEO, 0, clips, false, false);
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("format", "mp4");
            meta.put("platform.import.source", "fcpxml");
            meta.put("platform.otio.exportLossy", "true");

            return new TimelineSpec(
                    "fcpxml-import",
                    "FCPXML Import",
                    null,
                    List.of(video),
                    List.of(),
                    TimelineOutputSpec.mp4_1080p30(),
                    timelineStart,
                    meta);
        } catch (Exception e) {
            throw new IllegalArgumentException("FCPXML parse failed: " + e.getMessage(), e);
        }
    }

    private static double parseFcpTime(String value, double defaultSeconds) {
        if (value == null || value.isBlank()) {
            return defaultSeconds;
        }
        try {
            if (value.contains("/")) {
                String[] parts = value.split("/");
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultSeconds;
        }
    }
}
