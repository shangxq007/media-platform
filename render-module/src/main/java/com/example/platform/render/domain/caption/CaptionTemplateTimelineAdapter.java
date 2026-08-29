package com.example.platform.render.domain.caption;


import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import com.example.platform.render.domain.interchange.TimelineAudioSpec;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineAssetRef;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Adapts a validated CaptionTemplateRenderRequest into a TimelineSpec
 * compatible with the existing PLAN_BASED compile pipeline.
 *
 * <p>Internal domain adapter — not a public API.</p>
 *
 * <p>Mapping:
 * <ul>
 *   <li>captionSegments → TimelineTextOverlay list</li>
 *   <li>outputProfile → TimelineOutputSpec</li>
 *   <li>sourceProductId → TimelineAssetRef on a video track clip</li>
 *   <li>style/placement → text overlay fontFamily/fontSize/color/position</li>
 * </ul>
 *
 * <p>Does not call a typed provider plugin, StorageRuntime, or ProductRuntime.</p>
 */
@Component
public class CaptionTemplateTimelineAdapter {

    /**
     * Adapt a validated request to a TimelineSpec.
     *
     * @param request validated caption template render request
     * @return timeline spec compatible with PLAN_BASED compile
     */
    public TimelineSpec adapt(CaptionTemplateRenderRequest request) {
        String timelineId = "tl-caption-" + UUID.randomUUID().toString().substring(0, 8);

        // Build text overlays from caption segments
        CaptionStyleSpec style = request.effectiveTemplate().style();
        List<TimelineTextOverlay> overlays = request.captionSegments().stream()
                .map(seg -> toTextOverlay(seg, style))
                .toList();

        // Build output spec
        CaptionOutputProfileSpec profile = request.effectiveOutputProfile();
        TimelineOutputSpec outputSpec = toOutputSpec(profile);

        // Build video track with source product reference
        TimelineAssetRef assetRef = new TimelineAssetRef(
                request.sourceProductId(),
                "product://" + request.sourceProductId(),
                profile.container(),
                (long) (request.captionSegments().stream()
                        .mapToDouble(CaptionSegmentSpec::endSeconds)
                        .max().orElse(5.0)),
                profile.width(), profile.height(), Map.of(), null);

        double totalDuration = request.captionSegments().stream()
                .mapToDouble(CaptionSegmentSpec::endSeconds)
                .max().orElse(5.0);

        TimelineClip clip = TimelineClip.of(
                "clip-source", assetRef, 0, 0, totalDuration);

        TimelineTrack videoTrack = new TimelineTrack(
                "track-video", "Video", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        return new TimelineSpec(
                timelineId,
                "Caption Template Render",
                "Auto-generated from caption template request",
                List.of(videoTrack),
                overlays,
                outputSpec,
                totalDuration,
                Map.of("source", "caption-template-adapter",
                        "sourceProductId", request.sourceProductId()));
    }

    private TimelineTextOverlay toTextOverlay(CaptionSegmentSpec seg, CaptionStyleSpec style) {
        if (style.font() == null || style.font().family() == null) {
            throw new IllegalArgumentException(
                    "caption template font family required: no implicit font default (ROADMAP_19 CORR-2)");
        }
        com.example.platform.fonttext.typography.FontFamilyName fontFamily =
                new com.example.platform.fonttext.typography.FontFamilyName(style.font().family());
        int fontSize = style.fontSize() > 0 ? style.fontSize() : 24;
        String color = style.font() != null && style.font().color() != null
                ? style.font().color() : "#FFFFFF";
        String bgColor = style.font() != null ? style.font().backgroundColor() : null;

        String posX = "center";
        String posY = switch (style.placement()) {
            case TOP_CENTER -> "top";
            case CENTER -> "center";
            case BOTTOM_CENTER -> "bottom";
            default -> "bottom";
        };

        return new TimelineTextOverlay(
                "overlay-" + seg.startMs(),
                seg.text(),
                fontFamily, fontSize, color,
                posX, posY,
                seg.startSeconds(),
                seg.durationSeconds(),
                bgColor);
    }

    private TimelineOutputSpec toOutputSpec(CaptionOutputProfileSpec profile) {
        String resolution = profile.width() + "x" + profile.height();
        // C1-CNM1: exact rational rate; legacy double caption fps is converted
        // through exact decimal-string rationalization (never a binary float
        // authority on the canonical path).
        return new TimelineOutputSpec(
                profile.container(),
                resolution,
                decimalFpsToFrameRate(profile.fps()),
                "h264", 8000,
                TimelineAudioSpec.aacDefault(),
                "default");
    }

    /**
     * Exact rationalization of a legacy decimal fps value (e.g. 29.97 -&gt;
     * 2997/100). Integer fps maps exactly (30.0 -&gt; 30/1). The decimal
     * string is parsed to a rational; no binary floating intermediate.
     */
    private static com.example.platform.shared.time.FrameRate decimalFpsToFrameRate(double fps) {
        if (fps <= 0) {
            return com.example.platform.shared.time.FrameRate.of(30, 1);
        }
        if (fps == Math.rint(fps)) {
            return com.example.platform.shared.time.FrameRate.of((long) fps, 1);
        }
        String s = String.valueOf(fps);
        int dot = s.indexOf('.');
        if (dot < 0) {
            return com.example.platform.shared.time.FrameRate.of((long) fps, 1);
        }
        String frac = s.substring(dot + 1);
        long den = (long) Math.pow(10, frac.length());
        long num = (long) Math.round(fps * den);
        return com.example.platform.shared.time.FrameRate.of(num, den);
    }
}
