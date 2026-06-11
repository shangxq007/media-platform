package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DefaultRenderPlanner implements RenderPlanner {
    private static final Logger log = LoggerFactory.getLogger(DefaultRenderPlanner.class);

    private final RenderProviderRegistry registry;

    public DefaultRenderPlanner(RenderProviderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public RenderPlan plan(RenderJob job) {
        return switch (job.jobType()) {
            case "captioned_video_export" -> planCaptionedVideoExport(job);
            case "hls_package_export" -> planHlsPackageExport(job);
            case "timeline_export" -> planTimelineExport(job);
            default -> throw new IllegalArgumentException("Unsupported jobType: " + job.jobType());
        };
    }

    private RenderPlan planCaptionedVideoExport(RenderJob job) {
        List<RenderStep> steps = new ArrayList<>();
        List<String> selectedProviders = new ArrayList<>();
        List<String> requiredCapabilities = new ArrayList<>();

        steps.add(new RenderStep(
                "step-1-extract-audio", ProviderType.RENDER, "ffmpeg",
                List.of(Capabilities.EXTRACT_AUDIO), "input.mp4", "audio.wav",
                List.of(), true, List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.EXTRACT_AUDIO);

        if (job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS)) {
            steps.add(new RenderStep(
                    "step-2-caption-effects", ProviderType.RENDER, "remotion",
                    List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                    "input.mp4", "captioned.mp4",
                    List.of("step-1-extract-audio"), true, List.of()
            ));
            selectedProviders.add("remotion");
            requiredCapabilities.add(Capabilities.CAPTION_EFFECTS);
            requiredCapabilities.add(Capabilities.TEMPLATE_RENDER);
        }

        steps.add(new RenderStep(
                "step-3-output-normalize", ProviderType.RENDER, "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE),
                job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS) ? "captioned.mp4" : "input.mp4",
                "output.mp4",
                job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS) ? List.of("step-2-caption-effects") : List.of(),
                true, List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        return new RenderPlan(job.id(), steps, selectedProviders, requiredCapabilities,
                null, "1.0.0", 0.05, 30000);
    }

    private RenderPlan planHlsPackageExport(RenderJob job) {
        List<RenderStep> steps = new ArrayList<>();
        List<String> selectedProviders = new ArrayList<>();
        List<String> requiredCapabilities = new ArrayList<>();

        steps.add(new RenderStep(
                "step-1-output-normalize", ProviderType.RENDER, "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE), "input.mp4", "normalized.mp4",
                List.of(), true, List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        steps.add(new RenderStep(
                "step-2-package-hls", ProviderType.PACKAGING, "gpac",
                List.of(Capabilities.PACKAGE_HLS), "normalized.mp4", "manifest.m3u8",
                List.of("step-1-output-normalize"), true, List.of()
        ));
        selectedProviders.add("gpac");
        requiredCapabilities.add(Capabilities.PACKAGE_HLS);

        return new RenderPlan(job.id(), steps, selectedProviders, requiredCapabilities,
                null, "1.0.0", 0.10, 60000);
    }

    private RenderPlan planTimelineExport(RenderJob job) {
        List<RenderStep> steps = new ArrayList<>();
        List<String> selectedProviders = new ArrayList<>();
        List<String> requiredCapabilities = new ArrayList<>();

        steps.add(new RenderStep(
                "step-1-timeline-render", ProviderType.TIMELINE, "mlt",
                List.of(Capabilities.TIMELINE_RENDER), "timeline.json", "rendered.mp4",
                List.of(), true, List.of()
        ));
        selectedProviders.add("mlt");
        requiredCapabilities.add(Capabilities.TIMELINE_RENDER);

        steps.add(new RenderStep(
                "step-2-output-normalize", ProviderType.RENDER, "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE), "rendered.mp4", "output.mp4",
                List.of("step-1-timeline-render"), true, List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        return new RenderPlan(job.id(), steps, selectedProviders, requiredCapabilities,
                null, "1.0.0", 0.08, 45000);
    }

    @Override
    public Optional<ProviderMetadata> selectProvider(String capability, RenderJob job) {
        List<ProviderMetadata> eligible = getEligibleProviders(job).stream()
                .filter(m -> m.canHandleCapability(capability))
                .sorted(Comparator.comparingInt(m -> ProviderEligibility.scoreProvider(m, job)))
                .toList();

        if (eligible.isEmpty()) {
            log.warn("No eligible provider for capability '{}' in job '{}'", capability, job.id());
            return Optional.empty();
        }

        ProviderMetadata selected = eligible.getFirst();
        log.info("Selected provider '{}' for capability '{}' in job '{}' (score={})",
                selected.name(), capability, job.id(), ProviderEligibility.scoreProvider(selected, job));
        return Optional.of(selected);
    }

    @Override
    public List<ProviderMetadata> getEligibleProviders(RenderJob job) {
        return registry.getAllCapabilities().stream()
                .map(cap -> {
                    String key = cap.providerKey();
                    Optional<RenderProvider> provider = registry.getProvider(key);
                    if (provider.isEmpty()) return null;
                    return new ProviderMetadata(
                            key, provider.get().getStatus(), provider.get().getPriority(),
                            provider.get().getProviderType(),
                            provider.get().getCapabilities(), provider.get().getCapabilities(),
                            List.of(), provider.get().getLimitations().stream()
                                    .map(Object::toString).toList(),
                            provider.get().isAutoDispatch(), "server",
                            provider.get().getPurpose(), provider.get().getLimitations()
                    );
                })
                .filter(Objects::nonNull)
                .filter(m -> ProviderEligibility.isEligible(m, job))
                .distinct()
                .toList();
    }
}
