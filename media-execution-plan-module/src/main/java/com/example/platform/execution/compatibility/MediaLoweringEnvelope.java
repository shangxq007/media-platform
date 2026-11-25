package com.example.platform.execution.compatibility;

import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderRaster;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement;
import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Versioned, replayable input-shape contracts for the two current native media pipelines. */
public enum MediaLoweringEnvelope {
    GENERIC, TIMED_TRACKS_H264_V1, TIMED_TEXT_H264_V1;

    public boolean supports(PhysicalPlanUnit unit) {
        if (this == GENERIC) return true;
        var extent = unit.propagatedExtent();
        if (extent != null && !supportsRaster(extent.raster())) return false;
        if (unit.typedOutputs().size() != 1) return false;
        var output = unit.typedOutputs().getFirst();
        if (output.finalArtifactExpectations().size() + output.intermediateArtifactExpectations().size() > 1
                || output.outputRequirements().size() > 1 || output.outputRequirements().stream()
                        .anyMatch(requirement -> requirement.colorDescription().isPresent()
                                || requirement.rasterSample().isPresent())) return false;
        boolean tracks = this == TIMED_TRACKS_H264_V1;
        if (tracks && !unit.executionIntentRefs().isEmpty()) return false;
        String operation = unit.operationKey();
        if (unit.artifactCapabilityRequest() != null && !operation.equals("frame-extract")) return false;
        if (unit.temporalWindow() != null && !List.of("decode", "audio-decode", "frame-extract").contains(operation)) {
            return false;
        }
        return switch (operation) {
            case "decode" -> decode(unit, "video.decode");
            case "audio-decode" -> tracks && decode(unit, "audio.decode");
            case "composite" -> hasCapability(unit, "render.composite") && composite(unit, tracks);
            case "multitrack-composite" -> tracks && hasCapability(unit, "render.multitrack-composite") && timedTracks(unit);
            case "encode-multitrack" -> tracks && single(unit, "render.output");
            case "encode" -> single(unit, "render.output");
            case "transcode" -> unit.typedInputs().size() == 1
                    && (this == TIMED_TEXT_H264_V1 || hasCapability(unit, "media.transcode"));
            case "frame-extract" -> !tracks && frameExtraction(unit);
            case "raster" -> !tracks && timedText(unit);
            default -> false;
        };
    }

    /** The product bounds apply to exact MP4 delivery. A declaration may narrow both axes further. */
    public record OutputRasters(int minWidth, int minHeight, int maxWidth, int maxHeight,
            int maxPixels, int alignment) {
        boolean admits(RenderRaster raster) {
            return raster.width() >= minWidth && raster.width() <= maxWidth
                    && raster.height() >= minHeight && raster.height() <= maxHeight
                    && raster.width() % alignment == 0 && raster.height() % alignment == 0
                    && (long) raster.width() * raster.height() <= maxPixels;
        }
    }

    public java.util.Optional<OutputRasters> outputRasters() {
        return switch (this) {
            case GENERIC -> java.util.Optional.empty();
            case TIMED_TRACKS_H264_V1 -> java.util.Optional.of(new OutputRasters(
                    RenderRaster.MIN_DIMENSION, RenderRaster.MIN_DIMENSION,
                    RenderRaster.MAX_DIMENSION, RenderRaster.MAX_DIMENSION, RenderRaster.MAX_PIXELS, 2));
            case TIMED_TEXT_H264_V1 -> {
                var exact = RenderRaster.DEFAULT;
                yield java.util.Optional.of(new OutputRasters(exact.width(), exact.height(),
                        exact.width(), exact.height(), exact.width() * exact.height(), 2));
            }
        };
    }

    public boolean supportsRaster(RenderRaster raster) {
        return outputRasters().map(profile -> profile.admits(raster)).orElse(false);
    }

    private static boolean hasCapability(PhysicalPlanUnit unit, String capability) {
        return unit.capabilityRequirementRefs().stream()
                .anyMatch(ref -> ref.declaration().capabilityId().value().equals(capability));
    }

    private static boolean single(PhysicalPlanUnit unit, String capability) {
        return unit.typedInputs().size() == 1 && hasCapability(unit, capability);
    }

    private static boolean decode(PhysicalPlanUnit unit, String capability) {
        return single(unit, capability) && (unit.temporalWindow() == null || !unit.temporalWindow().isFreezePoint());
    }

    private static boolean frameExtraction(PhysicalPlanUnit unit) {
        var request = unit.artifactCapabilityRequest();
        if (request == null || !single(unit, request.capabilityId()) || unit.capabilityRequirementRefs().size() != 1) {
            return false;
        }
        var source = unit.typedInputs().getFirst().sourceArtifact();
        var finals = unit.typedOutputs().getFirst().finalArtifactExpectations();
        return source != null && request.source().artifactId().equals(source.artifactId())
                && request.source().contentDigest().matches(source.contentDigest())
                && unit.temporalWindow() != null && unit.temporalWindow().isFreezePoint()
                && unit.temporalWindow().start().equals(request.parameters().position())
                && finals.size() == 1 && finals.getFirst().role() == RenderOutputRole.DERIVED_MEDIA;
    }

    private static boolean timedText(PhysicalPlanUnit unit) {
        if (unit.propagatedExtent() == null || unit.executionCoverage() == null || !unit.typedInputs().isEmpty()
                || unit.capabilityRequirementRefs().size() != 1 || !hasCapability(unit, "subtitle.rasterize")) return false;
        var texts = unit.typedOutputs().getFirst().materializationRequirements().stream()
                .filter(TimedTextMaterializationRequirement.class::isInstance)
                .map(TimedTextMaterializationRequirement.class::cast).toList();
        if (texts.size() != 1) return false;
        return texts.getFirst().hasStyledContentWithin(unit.propagatedExtent());
    }

    private static boolean composite(PhysicalPlanUnit unit, boolean contiguous) {
        var extent = unit.propagatedExtent();
        if (extent == null || unit.typedInputs().isEmpty()) return false;
        var video = new ArrayList<RenderDependency.TimedCompositeInput>();
        for (var input : unit.typedInputs()) {
            if (input.dependencyVariant() instanceof RenderDependency.TimedCompositeInput timed) {
                if (timed.start().isLessThan(extent.start()) || timed.end().isGreaterThan(extent.end())) return false;
                video.add(timed);
            } else if (contiguous || !(input.dependencyVariant() instanceof RenderDependency.CompositeInput)) {
                return false;
            }
        }
        video.sort(Comparator.comparing(RenderDependency.TimedCompositeInput::start));
        MediaTime cursor = extent.start();
        for (var clip : video) {
            if (clip.start().isLessThan(cursor) || contiguous && !cursor.equals(clip.start())) return false;
            cursor = clip.end();
        }
        return !contiguous || cursor.equals(extent.end());
    }

    private static boolean timedTracks(PhysicalPlanUnit unit) {
        var extent = unit.propagatedExtent();
        if (extent == null || unit.typedInputs().isEmpty()) return false;
        record Lane(int layer, boolean audio) {}
        var lanes = new HashMap<Lane, List<RenderDependency.TimedTrackInput>>();
        for (var input : unit.typedInputs()) {
            if (!(input.dependencyVariant() instanceof RenderDependency.TimedTrackInput clip)
                    || clip.start().isLessThan(extent.start()) || clip.end().isGreaterThan(extent.end())) return false;
            var transform = clip.placement();
            if (!clip.audio() && !fitsIntermediateRaster(extent.raster(), transform.scaleX(),
                    transform.scaleY(), transform.degrees())) return false;
            lanes.computeIfAbsent(new Lane(clip.layer(), clip.audio()), ignored -> new ArrayList<>()).add(clip);
        }
        for (var clips : lanes.values()) {
            clips.sort(Comparator.comparing(RenderDependency.TimedTrackInput::start));
            for (int i = 1; i < clips.size(); i++) {
                if (clips.get(i - 1).end().isGreaterThan(clips.get(i).start())) return false;
            }
        }
        return true;
    }

    public static boolean fitsIntermediateRaster(RenderRaster raster, double scaleX, double scaleY, double degrees) {
        double width = Math.ceil(raster.width() * scaleX);
        double height = Math.ceil(raster.height() * scaleY);
        long limit = 8_294_400L;
        if (width * height > limit) return false;
        if (degrees % 90 == 0) return true;
        double radians = Math.toRadians(degrees);
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        return Math.ceil(width * cos + height * sin) * Math.ceil(width * sin + height * cos) <= limit;
    }
}
