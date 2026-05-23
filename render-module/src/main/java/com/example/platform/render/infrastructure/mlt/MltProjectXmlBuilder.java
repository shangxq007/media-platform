package com.example.platform.render.infrastructure.mlt;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import com.example.platform.render.domain.timeline.TimelineTrack;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds MLT project XML from a {@link TimelineSpec}.
 */
public class MltProjectXmlBuilder {

    private static final Logger log = LoggerFactory.getLogger(MltProjectXmlBuilder.class);
    private static final String QUOTE_REF = "&" + "quot;";
    private static final String APOS_REF = "&" + "apos;";

    public String build(TimelineSpec timeline) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='UTF-8'?>\n");

        TimelineOutputSpec output = timeline.outputSpec();
        int width = output != null ? output.width() : 1920;
        int height = output != null ? output.height() : 1080;
        double fps = output != null ? output.frameRate() : 30.0;

        xml.append(String.format("<mlt width=\"%d\" height=\"%d\" frame_rate_num=\"%.0f\">\n",
                width, height, fps));

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                if (track.clips() != null) {
                    for (TimelineClip clip : track.clips()) {
                        if (clip.assetRef() != null) {
                            xml.append(String.format(
                                    "  <producer id=\"%s\" resource=\"%s\" />\n",
                                    sanitizeId(clip.id()),
                                    escapeXml(clip.assetRef().storageUri())));
                        }
                    }
                }
            }
        }

        if (timeline.textOverlays() != null && !timeline.textOverlays().isEmpty()) {
            for (TimelineTextOverlay overlay : timeline.textOverlays()) {
                xml.append(String.format(
                        "  <producer id=\"text_%s\" in=\"0\" out=\"%d\">\n",
                        sanitizeId(overlay.id()),
                        (int) ((overlay.startTime() + overlay.duration()) * fps)));
                xml.append("    <property name=\"mlt_service\">pango</property>\n");
                xml.append(String.format("    <property name=\"text\">%s</property>\n",
                        escapeXml(overlay.text())));
                xml.append(String.format("    <property name=\"fgcolour\">%s</property>\n",
                        escapeXml(overlay.color() != null ? overlay.color() : "#FFFFFF")));
                xml.append("  </producer>\n");
            }
        }

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                xml.append(String.format("  <playlist id=\"%s\">\n", sanitizeId(track.id())));
                if (track.clips() != null) {
                    TimelineClip previous = null;
                    for (TimelineClip clip : track.clips()) {
                        if (previous != null && hasCrossDissolve(previous)) {
                            int transFrames = (int) (0.5 * fps);
                            xml.append(String.format(
                                    "    <transition mlt_service=\"luma\" in=\"0\" out=\"%d\">\n",
                                    transFrames));
                            xml.append("      <property name=\"start\">0.5</property>\n");
                            xml.append("      <property name=\"end\">1.0</property>\n");
                            xml.append("    </transition>\n");
                        }
                        int inFrame = (int) (clip.assetInPoint() * fps);
                        int outFrame = (int) (clip.assetOutPoint() * fps);
                        xml.append(String.format(
                                "    <entry producer=\"%s\" in=\"%d\" out=\"%d\">\n",
                                sanitizeId(clip.id()), inFrame, outFrame));
                        appendMltFilters(xml, clip, fps);
                        xml.append("    </entry>\n");
                        previous = clip;
                    }
                }
                xml.append("  </playlist>\n");
            }
        }

        xml.append("  <tractor>\n");
        xml.append("    <multitrack>\n");
        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                xml.append(String.format("      <track producer=\"%s\" />\n",
                        sanitizeId(track.id())));
            }
        }
        xml.append("    </multitrack>\n");
        if (timeline.textOverlays() != null) {
            for (TimelineTextOverlay overlay : timeline.textOverlays()) {
                xml.append(String.format("    <track producer=\"text_%s\" />\n", sanitizeId(overlay.id())));
            }
        }
        xml.append("  </tractor>\n");
        xml.append("</mlt>\n");

        log.debug("Built MLT XML for timeline: {}", timeline.id());
        return xml.toString();
    }

    /**
     * Builds a single-track MLT playlist that concatenates ordered segment media files.
     */
    public String buildSegmentConcat(List<SegmentMediaEntry> segments, int width, int height, double fps) {
        if (segments == null || segments.isEmpty()) {
            return buildSkeleton(width, height, fps);
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='UTF-8'?>\n");
        xml.append(String.format("<mlt width=\"%d\" height=\"%d\" frame_rate_num=\"%.0f\">\n",
                width, height, fps));
        for (SegmentMediaEntry segment : segments) {
            xml.append(String.format(
                    "  <producer id=\"%s\" resource=\"%s\" />\n",
                    sanitizeId(segment.id()),
                    escapeXml(segment.resourceUri())));
        }
        xml.append("  <playlist id=\"segment_concat\">\n");
        for (SegmentMediaEntry segment : segments) {
            xml.append(String.format("    <entry producer=\"%s\" />\n", sanitizeId(segment.id())));
        }
        xml.append("  </playlist>\n");
        xml.append("  <tractor>\n");
        xml.append("    <multitrack>\n");
        xml.append("      <track producer=\"segment_concat\" />\n");
        xml.append("    </multitrack>\n");
        xml.append("  </tractor>\n");
        xml.append("</mlt>\n");
        return xml.toString();
    }

    public record SegmentMediaEntry(String id, String resourceUri) {}

    public String buildSkeleton(int width, int height, double fps) {
        return String.format(
                "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<mlt width=\"%d\" height=\"%d\" frame_rate_num=\"%.0f\">\n"
                + "  <tractor>\n"
                + "    <multitrack>\n"
                + "    </multitrack>\n"
                + "  </tractor>\n"
                + "</mlt>\n",
                width, height, fps);
    }

    private void appendMltFilters(StringBuilder xml, TimelineClip clip, double fps) {
        if (clip.effects() == null || clip.effects().isEmpty()) {
            return;
        }
        for (TimelineClipEffect effect : clip.effects()) {
            String mltService = toMltService(effect.effectKey());
            if (mltService == null) {
                continue;
            }
            xml.append(String.format("      <filter mlt_service=\"%s\">\n", mltService));
            if (effect.parameters() != null) {
                effect.parameters().forEach((key, value) ->
                        xml.append(String.format(
                                "        <property name=\"%s\">%s</property>\n",
                                escapeXml(key), escapeXml(String.valueOf(value)))));
            }
            xml.append("      </filter>\n");
        }
    }

    private String toMltService(String effectKey) {
        return switch (effectKey) {
            case "video.fade_in", "video.fade_out" -> "brightness";
            case "video.blur" -> "boxblur";
            case "video.brightness" -> "brightness";
            case "video.contrast" -> "brightness";
            case "video.grayscale" -> "greyscale";
            case "video.cross_dissolve" -> "luma";
            default -> null;
        };
    }

    private boolean hasCrossDissolve(TimelineClip clip) {
        if (clip.effects() == null) {
            return false;
        }
        return clip.effects().stream()
                .anyMatch(e -> "video.cross_dissolve".equals(e.effectKey()));
    }

    private String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String escapeXml(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': sb.append("&"); break;
                case '<': sb.append("<"); break;
                case '>': sb.append(">"); break;
                case '"': sb.append(QUOTE_REF); break;
                case '\'': sb.append(APOS_REF); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
