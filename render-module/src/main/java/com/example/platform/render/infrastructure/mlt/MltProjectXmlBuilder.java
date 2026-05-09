package com.example.platform.render.infrastructure.mlt;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
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

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                xml.append(String.format("  <playlist id=\"%s\">\n", sanitizeId(track.id())));
                if (track.clips() != null) {
                    for (TimelineClip clip : track.clips()) {
                        int inFrame = (int) (clip.assetInPoint() * fps);
                        int outFrame = (int) (clip.assetOutPoint() * fps);
                        xml.append(String.format(
                                "    <entry producer=\"%s\" in=\"%d\" out=\"%d\" />\n",
                                sanitizeId(clip.id()), inFrame, outFrame));
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
        xml.append("  </tractor>\n");
        xml.append("</mlt>\n");

        log.debug("Built MLT XML for timeline: {}", timeline.id());
        return xml.toString();
    }

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
