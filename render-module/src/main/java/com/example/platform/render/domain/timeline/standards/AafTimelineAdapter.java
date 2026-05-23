package com.example.platform.render.domain.timeline.standards;

import com.example.platform.render.domain.timeline.TimelineAssetRef;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AAF interchange: full binary AAF requires external worker; in-process supports JSON/XML manifest.
 */
public final class AafTimelineAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AafTimelineAdapter() {
    }

    public static TimelineSpec importFromSource(String aafPath, String manifestContent, String defaultMediaUri) {
        if (manifestContent != null && !manifestContent.isBlank()) {
            String trimmed = manifestContent.trim();
            if (trimmed.startsWith("{")) {
                return parseJsonManifest(trimmed, defaultMediaUri);
            }
            if (trimmed.startsWith("<")) {
                return parseXmlManifest(trimmed, defaultMediaUri);
            }
        }
        return importPlaceholder(aafPath, defaultMediaUri);
    }

    public static TimelineSpec importPlaceholder(String aafPath, String defaultMediaUri) {
        Map<String, String> meta = baseMeta(aafPath, defaultMediaUri);
        meta.put("platform.import.status", "PLACEHOLDER_REQUIRES_WORKER");

        TimelineTrack video = TimelineTrack.of("aaf-v1", "AAF Video", TimelineTrack.TrackType.VIDEO);
        return new TimelineSpec(
                "aaf-import",
                "AAF Import (pending)",
                "AAF file registered; convert via dedicated worker before render",
                List.of(video),
                List.of(),
                TimelineOutputSpec.mp4_1080p30(),
                0,
                meta);
    }

    public static TimelineSpec parseJsonManifest(String json, String defaultMediaUri) {
        try {
            JsonNode root = MAPPER.readTree(json);
            List<TimelineClip> clips = new ArrayList<>();
            double timelinePos = 0;
            int index = 0;
            if (root.has("slots") && root.get("slots").isArray()) {
                for (JsonNode slot : root.get("slots")) {
                    double duration = slot.path("duration").asDouble(5.0);
                    double start = slot.has("timelineStart")
                            ? slot.path("timelineStart").asDouble(timelinePos)
                            : timelinePos;
                    String uri = slot.has("mediaUri")
                            ? slot.path("mediaUri").asText()
                            : (defaultMediaUri != null ? defaultMediaUri : "aaf://slot/" + slot.path("id").asText("s"));
                    clips.add(TimelineClip.of(
                            "aaf-" + (++index),
                            TimelineAssetRef.of("aaf-asset-" + index, uri),
                            start,
                            0,
                            duration));
                    timelinePos = Math.max(timelinePos, start + duration);
                }
            }
            Map<String, String> meta = baseMeta(null, defaultMediaUri);
            meta.put("platform.import.status", "MANIFEST_JSON");
            meta.put("platform.import.manifest", json.length() > 4000 ? json.substring(0, 4000) + "…" : json);

            TimelineTrack video = new TimelineTrack("aaf-v1", "AAF Video",
                    TimelineTrack.TrackType.VIDEO, 0, clips, false, false);
            return new TimelineSpec(
                    root.path("id").asText("aaf-import"),
                    root.path("name").asText("AAF Import"),
                    root.path("description").asText(null),
                    List.of(video),
                    List.of(),
                    TimelineOutputSpec.mp4_1080p30(),
                    timelinePos,
                    meta);
        } catch (Exception e) {
            throw new IllegalArgumentException("AAF JSON manifest parse failed: " + e.getMessage(), e);
        }
    }

    public static TimelineSpec parseXmlManifest(String xml, String defaultMediaUri) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            List<TimelineClip> clips = new ArrayList<>();
            NodeList slots = doc.getElementsByTagName("slot");
            double timelinePos = 0;
            int index = 0;
            for (int i = 0; i < slots.getLength(); i++) {
                Element slot = (Element) slots.item(i);
                double duration = parseDouble(slot.getAttribute("duration"), 5.0);
                double start = parseDouble(slot.getAttribute("timelineStart"), timelinePos);
                String uri = slot.hasAttribute("mediaUri")
                        ? slot.getAttribute("mediaUri")
                        : (defaultMediaUri != null ? defaultMediaUri : "aaf://slot/" + slot.getAttribute("id"));
                clips.add(TimelineClip.of(
                        "aaf-" + (++index),
                        TimelineAssetRef.of("aaf-asset-" + index, uri),
                        start,
                        0,
                        duration));
                timelinePos = Math.max(timelinePos, start + duration);
            }
            Map<String, String> meta = baseMeta(null, defaultMediaUri);
            meta.put("platform.import.status", "MANIFEST_XML");

            Element root = doc.getDocumentElement();
            String id = root != null && root.hasAttribute("id") ? root.getAttribute("id") : "aaf-import";
            String name = root != null && root.hasAttribute("name") ? root.getAttribute("name") : "AAF Import";

            TimelineTrack video = new TimelineTrack("aaf-v1", "AAF Video",
                    TimelineTrack.TrackType.VIDEO, 0, clips, false, false);
            return new TimelineSpec(id, name, null, List.of(video), List.of(),
                    TimelineOutputSpec.mp4_1080p30(), timelinePos, meta);
        } catch (Exception e) {
            throw new IllegalArgumentException("AAF XML manifest parse failed: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> baseMeta(String aafPath, String defaultMediaUri) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("format", "mp4");
        meta.put("platform.import.source", "aaf");
        meta.put("platform.import.aafPath", aafPath != null ? aafPath : "");
        meta.put("platform.import.defaultMediaUri", defaultMediaUri != null ? defaultMediaUri : "");
        meta.put("platform.otio.exportLossy", "true");
        return meta;
    }

    private static double parseDouble(String value, double defaultVal) {
        if (value == null || value.isBlank()) {
            return defaultVal;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
