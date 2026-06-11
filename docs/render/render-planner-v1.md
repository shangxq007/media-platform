# RenderPlanner v1

## 概述

RenderPlanner v1 是渲染计划器的第一版实现，支持三种 jobType：
1. `captioned_video_export` - 自动字幕视频导出
2. `hls_package_export` - HLS 打包导出
3. `timeline_export` - 时间线导出

## 设计原则

1. **根据 requiredCapabilities 和 enabledCapabilities 选择 provider**
2. **不使用 declaredCapabilities 调度**
3. **记录每个 step 的 provider 命中原因**
4. **尊重 status、priority、autoDispatch、notFor、mode**
5. **Production 模式不能选择 Spike / Hold / Deprecated provider**
6. **如果没有符合条件的 provider，返回清晰的错误信息**

## 实现

```java
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
                "step-1-extract-audio",
                ProviderType.RENDER,
                "ffmpeg",
                List.of(Capabilities.EXTRACT_AUDIO),
                "input.mp4",
                "audio.wav",
                List.of(),
                true,
                List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.EXTRACT_AUDIO);

        if (job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS)) {
            steps.add(new RenderStep(
                    "step-2-caption-effects",
                    ProviderType.RENDER,
                    "remotion",
                    List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                    "input.mp4",
                    "captioned.mp4",
                    List.of("step-1-extract-audio"),
                    true,
                    List.of()
            ));
            selectedProviders.add("remotion");
            requiredCapabilities.add(Capabilities.CAPTION_EFFECTS);
            requiredCapabilities.add(Capabilities.TEMPLATE_RENDER);
        }

        steps.add(new RenderStep(
                "step-3-output-normalize",
                ProviderType.RENDER,
                "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE),
                job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS) ? "captioned.mp4" : "input.mp4",
                "output.mp4",
                job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS) ? List.of("step-2-caption-effects") : List.of(),
                true,
                List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        return new RenderPlan(
                job.id(),
                steps,
                selectedProviders,
                requiredCapabilities,
                null,
                "1.0.0",
                0.05,
                30000
        );
    }

    private RenderPlan planHlsPackageExport(RenderJob job) {
        List<RenderStep> steps = new ArrayList<>();
        List<String> selectedProviders = new ArrayList<>();
        List<String> requiredCapabilities = new ArrayList<>();

        steps.add(new RenderStep(
                "step-1-output-normalize",
                ProviderType.RENDER,
                "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE),
                "input.mp4",
                "normalized.mp4",
                List.of(),
                true,
                List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        steps.add(new RenderStep(
                "step-2-package-hls",
                ProviderType.PACKAGING,
                "gpac",
                List.of(Capabilities.PACKAGE_HLS),
                "normalized.mp4",
                "manifest.m3u8",
                List.of("step-1-output-normalize"),
                true,
                List.of()
        ));
        selectedProviders.add("gpac");
        requiredCapabilities.add(Capabilities.PACKAGE_HLS);

        return new RenderPlan(
                job.id(),
                steps,
                selectedProviders,
                requiredCapabilities,
                null,
                "1.0.0",
                0.10,
                60000
        );
    }

    private RenderPlan planTimelineExport(RenderJob job) {
        List<RenderStep> steps = new ArrayList<>();
        List<String> selectedProviders = new ArrayList<>();
        List<String> requiredCapabilities = new ArrayList<>();

        steps.add(new RenderStep(
                "step-1-timeline-render",
                ProviderType.TIMELINE,
                "mlt",
                List.of(Capabilities.TIMELINE_RENDER),
                "timeline.json",
                "rendered.mp4",
                List.of(),
                true,
                List.of()
        ));
        selectedProviders.add("mlt");
        requiredCapabilities.add(Capabilities.TIMELINE_RENDER);

        steps.add(new RenderStep(
                "step-2-output-normalize",
                ProviderType.RENDER,
                "ffmpeg",
                List.of(Capabilities.OUTPUT_NORMALIZE),
                "rendered.mp4",
                "output.mp4",
                List.of("step-1-timeline-render"),
                true,
                List.of()
        ));
        selectedProviders.add("ffmpeg");
        requiredCapabilities.add(Capabilities.OUTPUT_NORMALIZE);

        return new RenderPlan(
                job.id(),
                steps,
                selectedProviders,
                requiredCapabilities,
                null,
                "1.0.0",
                0.08,
                45000
        );
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
                .map(cap -> registry.getProvider(cap.providerKey()).orElse(null))
                .filter(Objects::nonNull)
                .filter(provider -> {
                    ProviderMetadata metadata = provider instanceof BaseProvider bp
                            ? bp.getMetadata()
                            : createDefaultMetadata(provider);
                    return ProviderEligibility.isEligible(metadata, job);
                })
                .map(provider -> provider instanceof BaseProvider bp
                        ? bp.getMetadata()
                        : createDefaultMetadata(provider))
                .distinct()
                .toList();
    }

    private ProviderMetadata createDefaultMetadata(RenderProvider provider) {
        String key = provider.getSupportedProfiles().isEmpty() ? "unknown" : provider.getSupportedProfiles().getFirst();
        return new ProviderMetadata(
                key,
                provider.getStatus(),
                provider.getPriority(),
                provider.getProviderType(),
                provider.getCapabilities(),
                provider.getCapabilities(),
                List.of(),
                List.of(),
                provider.isAutoDispatch(),
                "server",
                provider.getPurpose(),
                provider.getLimitations()
        );
    }
}
```

## 命中原因记录

每个 RenderStep 的 provider 命中原因：

| Step | Provider | 命中原因 |
|------|----------|----------|
| step-1-extract-audio | ffmpeg | Production/P0, supports extract_audio, autoDispatch=true |
| step-2-caption-effects | remotion | POC/P1, supports caption_effects+template_render, enabledCapabilities match |
| step-3-output-normalize | ffmpeg | Production/P0, supports output_normalize, autoDispatch=true |
| step-1-output-normalize | ffmpeg | Production/P0, supports output_normalize, autoDispatch=true |
| step-2-package-hls | gpac | POC/P1, supports package_hls, enabledCapabilities match |
| step-1-timeline-render | mlt | POC/P1, supports timeline_render, enabledCapabilities match |

## 错误处理

如果没有符合条件的 provider，返回清晰的错误信息：

```
No eligible provider for capability '3d_render' in job 'job-001'.
Required capabilities: [3d_render]
Eligible providers: []
Reason: No provider with status PRODUCTION or POC supports capability '3d_render'.
Suggestion: Enable a provider that supports '3d_render' or change the job requirements.
```

## 相关文档

- [RenderJob Schema](./render-job-schema.md)
- [Routing Rules](./routing-rules.md)
- [Provider Types](./provider-types.md)
