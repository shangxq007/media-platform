package com.example.platform.render.infrastructure.otio;

import com.example.platform.render.infrastructure.RenderConstraints;
import com.example.platform.render.infrastructure.RenderJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OTIOTimelineCompiler {
    private static final Logger log = LoggerFactory.getLogger(OTIOTimelineCompiler.class);

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String METADATA_NAMESPACE = "platform";

    public OTIOTimelineSummary compile(String otioJson, Map<String, Object> metadata) {
        log.info("Compiling OTIO timeline");

        String schemaVersion = extractSchemaVersion(metadata);
        if (schemaVersion == null) {
            log.warn("metadata.bluepulse.schemaVersion missing, using default: {}", SCHEMA_VERSION);
            schemaVersion = SCHEMA_VERSION;
        }

        List<OTIOClipSummary> videoClips = new ArrayList<>();
        List<OTIOClipSummary> audioClips = new ArrayList<>();
        List<OTIOCaptionRef> captionRefs = new ArrayList<>();
        List<OTIOFontRef> fontRefs = new ArrayList<>();
        List<OTIOTemplateRef> templateRefs = new ArrayList<>();
        List<OTIOEffectRef> effectRefs = new ArrayList<>();

        Object bluepulseObj = metadata.get(METADATA_NAMESPACE);
        if (bluepulseObj instanceof Map<?, ?> bpMap) {
            extractCaptionRefs(bpMap, captionRefs);
            extractFontRefs(bpMap, fontRefs);
            extractTemplateRefs(bpMap, templateRefs);
            extractEffectRefs(bpMap, effectRefs);
        }

        OTIORenderHints renderHints = extractRenderHints(metadata);

        return new OTIOTimelineSummary(
                schemaVersion,
                extractProjectId(metadata),
                extractTimelineId(metadata),
                0.0,
                1, 0, 1,
                List.of(new OTIOTrackSummary("video-1", "Video", "video", videoClips)),
                List.of(),
                List.of(new OTIOTrackSummary("subtitle-1", "Subtitle", "subtitle", List.of())),
                captionRefs, fontRefs, templateRefs, effectRefs,
                renderHints
        );
    }

    public RenderJob generateRenderJob(OTIOTimelineSummary summary, String mode) {
        List<String> requiredCapabilities = new ArrayList<>(summary.renderHints().requiredCapabilities());
        if (summary.captionRefs() != null && !summary.captionRefs().isEmpty()) {
            if (!requiredCapabilities.contains("caption_effects")) {
                requiredCapabilities.add("caption_effects");
            }
        }

        String styleJson = buildStyleJson(summary);
        String captionsJson = buildCaptionsJson(summary);

        return new RenderJob(
                "job-otio-" + summary.timelineId(),
                "captioned_video_export",
                mode,
                summary.renderHints().outputWidth() + "x" + summary.renderHints().outputHeight(),
                List.of(),
                "{}", captionsJson, styleJson,
                summary.renderHints().outputFormat(),
                requiredCapabilities,
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of()
        );
    }

    private String extractSchemaVersion(Map<String, Object> metadata) {
        Object bpObj = metadata.get(METADATA_NAMESPACE);
        if (bpObj instanceof Map<?, ?> bpMap) {
            Object ver = bpMap.get("schemaVersion");
            return ver != null ? ver.toString() : null;
        }
        return null;
    }

    private String extractProjectId(Map<String, Object> metadata) {
        Object bpObj = metadata.get(METADATA_NAMESPACE);
        if (bpObj instanceof Map<?, ?> bpMap) {
            Object id = bpMap.get("projectId");
            return id != null ? id.toString() : "unknown";
        }
        return "unknown";
    }

    private String extractTimelineId(Map<String, Object> metadata) {
        Object bpObj = metadata.get(METADATA_NAMESPACE);
        if (bpObj instanceof Map<?, ?> bpMap) {
            Object id = bpMap.get("timelineId");
            return id != null ? id.toString() : "unknown";
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private void extractCaptionRefs(Map<?, ?> bpMap, List<OTIOCaptionRef> refs) {
        Object captionsObj = bpMap.get("captions");
        if (captionsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> capMap) {
                    refs.add(new OTIOCaptionRef(
                            capMap.get("id") != null ? capMap.get("id").toString() : "unknown",
                            capMap.get("assetRef") != null ? capMap.get("assetRef").toString() : null,
                            capMap.get("startTime") instanceof Number n ? n.doubleValue() : 0.0,
                            capMap.get("endTime") instanceof Number n ? n.doubleValue() : 0.0,
                            capMap.get("styleRef") != null ? capMap.get("styleRef").toString() : null,
                            capMap.get("templateRef") != null ? capMap.get("templateRef").toString() : null
                    ));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void extractFontRefs(Map<?, ?> bpMap, List<OTIOFontRef> refs) {
        Object fontsObj = bpMap.get("fonts");
        if (fontsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> fontMap) {
                    refs.add(new OTIOFontRef(
                            fontMap.get("refId") != null ? fontMap.get("refId").toString() : "unknown",
                            fontMap.get("assetId") != null ? fontMap.get("assetId").toString() : "unknown",
                            fontMap.get("fontFamily") != null ? fontMap.get("fontFamily").toString() : "unknown",
                            fontMap.get("fontWeight") != null ? fontMap.get("fontWeight").toString() : "400",
                            fontMap.get("fontStyle") != null ? fontMap.get("fontStyle").toString() : "normal",
                            fontMap.get("subsetRef") != null ? fontMap.get("subsetRef").toString() : null
                    ));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void extractTemplateRefs(Map<?, ?> bpMap, List<OTIOTemplateRef> refs) {
        Object templatesObj = bpMap.get("templates");
        if (templatesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> tmplMap) {
                    Map<String, Object> params = Map.of();
                    if (tmplMap.get("params") instanceof Map<?, ?> pMap) {
                        params = (Map<String, Object>) pMap;
                    }
                    refs.add(new OTIOTemplateRef(
                            tmplMap.get("refId") != null ? tmplMap.get("refId").toString() : "unknown",
                            tmplMap.get("templateId") != null ? tmplMap.get("templateId").toString() : "unknown",
                            tmplMap.get("templateVersion") != null ? tmplMap.get("templateVersion").toString() : "1.0.0",
                            params
                    ));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void extractEffectRefs(Map<?, ?> bpMap, List<OTIOEffectRef> refs) {
        Object effectsObj = bpMap.get("effects");
        if (effectsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> effMap) {
                    Map<String, Object> params = Map.of();
                    if (effMap.get("params") instanceof Map<?, ?> pMap) {
                        params = (Map<String, Object>) pMap;
                    }
                    refs.add(new OTIOEffectRef(
                            effMap.get("refId") != null ? effMap.get("refId").toString() : "unknown",
                            effMap.get("effectId") != null ? effMap.get("effectId").toString() : "unknown",
                            effMap.get("effectVersion") != null ? effMap.get("effectVersion").toString() : "1.0.0",
                            effMap.get("startTime") instanceof Number n ? n.doubleValue() : 0.0,
                            effMap.get("duration") instanceof Number n ? n.doubleValue() : 0.0,
                            params
                    ));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private OTIORenderHints extractRenderHints(Map<String, Object> metadata) {
        Object bpObj = metadata.get(METADATA_NAMESPACE);
        if (bpObj instanceof Map<?, ?> bpMap) {
            Object hintsObj = bpMap.get("renderHints");
            if (hintsObj instanceof Map<?, ?> hintsMap) {
                List<String> caps = List.of();
                if (hintsMap.get("requiredCapabilities") instanceof List<?> capList) {
                    caps = capList.stream().map(Object::toString).toList();
                }
                Map<String, Object> extra = Map.of();
                if (hintsMap.get("extra") instanceof Map<?, ?> eMap) {
                    extra = (Map<String, Object>) eMap;
                }
                return new OTIORenderHints(
                        hintsMap.get("outputFormat") != null ? hintsMap.get("outputFormat").toString() : "mp4",
                        hintsMap.get("outputWidth") instanceof Number n ? n.intValue() : 1920,
                        hintsMap.get("outputHeight") instanceof Number n ? n.intValue() : 1080,
                        hintsMap.get("outputFps") instanceof Number n ? n.intValue() : 30,
                        hintsMap.get("preferredNormalizeProvider") != null ? hintsMap.get("preferredNormalizeProvider").toString() : "ffmpeg",
                        caps, extra
                );
            }
        }
        return new OTIORenderHints("mp4", 1920, 1080, 30, "ffmpeg", List.of(), Map.of());
    }

    private String buildStyleJson(OTIOTimelineSummary summary) {
        if (summary.fontRefs() != null && !summary.fontRefs().isEmpty()) {
            OTIOFontRef font = summary.fontRefs().getFirst();
            return String.format("{\"fontRef\":\"%s\",\"fontFamily\":\"%s\",\"fontWeight\":\"%s\",\"fontStyle\":\"%s\"}",
                    font.assetId(), font.fontFamily(), font.fontWeight(), font.fontStyle());
        }
        return "{}";
    }

    private String buildCaptionsJson(OTIOTimelineSummary summary) {
        if (summary.captionRefs() == null || summary.captionRefs().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < summary.captionRefs().size(); i++) {
            OTIOCaptionRef cap = summary.captionRefs().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(cap.captionId()).append("\",");
            sb.append("\"assetRef\":\"").append(cap.assetRef()).append("\",");
            sb.append("\"startTime\":").append(cap.startTime()).append(",");
            sb.append("\"endTime\":").append(cap.endTime()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
