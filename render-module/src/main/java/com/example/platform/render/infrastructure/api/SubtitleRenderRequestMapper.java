package com.example.platform.render.infrastructure.api;

import com.example.platform.render.infrastructure.RenderConstraints;
import com.example.platform.render.infrastructure.RenderJob;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps public subtitle render requests to internal RenderJob.
 *
 * Validation rules:
 * - templateId must be from allowlist
 * - fontAssetId must belong to tenant
 * - fontAsset must be READY or READY_WITH_SUBSETS
 * - output format must be mp4
 * - user cannot specify provider
 * - user cannot pass raw Remotion JS or FFmpeg commands
 * - caption count, duration, text length must be within limits
 */
public class SubtitleRenderRequestMapper {

    private final TemplateAllowlist templateAllowlist;
    private final FontOwnershipChecker fontOwnershipChecker;

    public SubtitleRenderRequestMapper(TemplateAllowlist templateAllowlist,
                                        FontOwnershipChecker fontOwnershipChecker) {
        this.templateAllowlist = templateAllowlist;
        this.fontOwnershipChecker = fontOwnershipChecker;
    }

    public RenderJob mapToRenderJob(PublicSubtitleRenderRequest request, String tenantId) {
        validate(request, tenantId);

        String styleJson = buildStyleJson(request);
        String captionsJson = buildCaptionsJson(request);
        String outputJson = buildOutputJson(request);

        List<String> requiredCapabilities = new ArrayList<>();
        requiredCapabilities.add("caption_effects");
        requiredCapabilities.add("template_render");
        requiredCapabilities.add("output_normalize");

        String inputJson = buildInputJson(request);

        return new RenderJob(
                "job-subtitle-" + System.currentTimeMillis(),
                "captioned_video_export",
                "production",
                request.output().width() + "x" + request.output().height(),
                List.of(request.video().assetId()),
                "{}",
                captionsJson,
                styleJson,
                request.output().format(),
                requiredCapabilities,
                new RenderConstraints(
                        request.output().width(),
                        request.output().height(),
                        request.output().fps(),
                        PublicSubtitleRenderRequest.MAX_VIDEO_DURATION_SECONDS,
                        null,
                        null
                ),
                true,
                List.of(),
                List.of()
        );
    }

    public void validate(PublicSubtitleRenderRequest request, String tenantId) {
        if (request.video() == null || request.video().assetId() == null || request.video().assetId().isBlank()) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("video.assetId is required"));
        }

        if (request.captions() == null || request.captions().isEmpty()) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("captions are required"));
        }

        if (request.captions().size() > PublicSubtitleRenderRequest.MAX_CAPTIONS) {
            throw new SubtitleRenderValidationException(PublicApiError.captionLimitExceeded(request.captions().size()));
        }

        for (PublicCaption caption : request.captions()) {
            if (caption.text() == null || caption.text().isBlank()) {
                throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("caption text is required"));
            }
            if (caption.text().length() > PublicSubtitleRenderRequest.MAX_CAPTION_TEXT_LENGTH) {
                throw new SubtitleRenderValidationException(PublicApiError.invalidRequest(
                        "caption text exceeds max length: " + caption.text().length()));
            }
            if (caption.endTime() - caption.startTime() > PublicSubtitleRenderRequest.MAX_CAPTION_DURATION_SECONDS) {
                throw new SubtitleRenderValidationException(PublicApiError.durationLimitExceeded());
            }
        }

        if (request.template() == null || request.template().templateId() == null) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("template.templateId is required"));
        }

        if (!templateAllowlist.isAllowed(request.template().templateId())) {
            throw new SubtitleRenderValidationException(PublicApiError.templateNotAllowed(request.template().templateId()));
        }

        if (request.style() == null || request.style().fontAssetId() == null) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("style.fontAssetId is required"));
        }

        if (!fontOwnershipChecker.isOwnedByTenant(request.style().fontAssetId(), tenantId)) {
            throw new SubtitleRenderValidationException(PublicApiError.fontNotOwned(request.style().fontAssetId()));
        }

        if (!fontOwnershipChecker.isReady(request.style().fontAssetId())) {
            throw new SubtitleRenderValidationException(PublicApiError.fontNotReady(request.style().fontAssetId()));
        }

        if (request.output() == null) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("output is required"));
        }

        if (!PublicSubtitleRenderRequest.SUPPORTED_OUTPUT_FORMAT.equals(request.output().format())) {
            throw new SubtitleRenderValidationException(PublicApiError.unsupportedFormat(request.output().format()));
        }

        if (request.output().width() != null && request.output().width() > PublicSubtitleRenderRequest.MAX_OUTPUT_WIDTH) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("width exceeds max"));
        }

        if (request.output().height() != null && request.output().height() > PublicSubtitleRenderRequest.MAX_OUTPUT_HEIGHT) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("height exceeds max"));
        }

        if (request.output().fps() != null && request.output().fps() > PublicSubtitleRenderRequest.MAX_OUTPUT_FPS) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("fps exceeds max"));
        }

        if (request.style().fontSize() != null && (request.style().fontSize() < PublicSubtitleRenderRequest.MIN_FONT_SIZE
                || request.style().fontSize() > PublicSubtitleRenderRequest.MAX_FONT_SIZE)) {
            throw new SubtitleRenderValidationException(PublicApiError.invalidRequest("fontSize out of range"));
        }
    }

    private String buildStyleJson(PublicSubtitleRenderRequest request) {
        PublicCaptionStyle s = request.style();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"fontRef\":\"").append(s.fontAssetId()).append("\"");
        if (s.fontSize() != null) sb.append(",\"fontSize\":").append(s.fontSize());
        if (s.fontColor() != null) sb.append(",\"fontColor\":\"").append(s.fontColor()).append("\"");
        if (s.backgroundColor() != null) sb.append(",\"backgroundColor\":\"").append(s.backgroundColor()).append("\"");
        if (s.alignment() != null) sb.append(",\"alignment\":\"").append(s.alignment()).append("\"");
        if (s.position() != null) sb.append(",\"position\":\"").append(s.position()).append("\"");
        if (s.bold() != null) sb.append(",\"bold\":").append(s.bold());
        if (s.italic() != null) sb.append(",\"italic\":").append(s.italic());
        if (s.opacity() != null) sb.append(",\"opacity\":").append(s.opacity());
        sb.append("}");
        return sb.toString();
    }

    private String buildCaptionsJson(PublicSubtitleRenderRequest request) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < request.captions().size(); i++) {
            PublicCaption cap = request.captions().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"text\":\"").append(cap.text().replace("\"", "\\\"")).append("\"");
            sb.append(",\"startTime\":").append(cap.startTime());
            sb.append(",\"endTime\":").append(cap.endTime());
            if (cap.words() != null && !cap.words().isEmpty()) {
                sb.append(",\"words\":[");
                for (int j = 0; j < cap.words().size(); j++) {
                    PublicCaptionWord w = cap.words().get(j);
                    if (j > 0) sb.append(",");
                    sb.append("{\"text\":\"").append(w.text().replace("\"", "\\\"")).append("\"");
                    sb.append(",\"startTime\":").append(w.startTime());
                    sb.append(",\"endTime\":").append(w.endTime());
                    sb.append("}");
                }
                sb.append("]");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildOutputJson(PublicSubtitleRenderRequest request) {
        PublicOutputSpec o = request.output();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"format\":\"").append(o.format()).append("\"");
        if (o.width() != null) sb.append(",\"width\":").append(o.width());
        if (o.height() != null) sb.append(",\"height\":").append(o.height());
        if (o.fps() != null) sb.append(",\"fps\":").append(o.fps());
        sb.append("}");
        return sb.toString();
    }

    private String buildInputJson(PublicSubtitleRenderRequest request) {
        return "{\"video\":{\"assetId\":\"" + request.video().assetId() + "\"}}";
    }
}
