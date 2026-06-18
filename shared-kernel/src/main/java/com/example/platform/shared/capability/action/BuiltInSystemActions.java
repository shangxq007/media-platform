package com.example.platform.shared.capability.action;

import com.example.platform.shared.capability.CapabilityStability;
import com.example.platform.shared.capability.SystemAction;
import com.example.platform.shared.capability.registry.SystemActionRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Built-in system action metadata catalog.
 *
 * <p>BuiltInSystemActions provides metadata for first-party platform actions.
 * These actions are metadata-only and do not implement execution logic.</p>
 *
 * <p><strong>Contract only:</strong> This defines action metadata.
 * Runtime execution is not implemented.</p>
 */
public final class BuiltInSystemActions {

    private BuiltInSystemActions() {
        // Utility class
    }

    // ===== RENDER Actions =====

    /**
     * Create a render job.
     */
    public static final MetadataSystemAction RENDER_CREATE_JOB = new MetadataSystemAction(
        "render.create_job",
        "Create Render Job",
        "Create a new render job for media processing",
        "1.0.0",
        "schema://render-create-job-input",
        "schema://render-create-job-output",
        Set.of("render.create", "media.read"),
        Duration.ofMinutes(5),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.RENDER
    );

    /**
     * Generate HLS preview for media.
     */
    public static final MetadataSystemAction RENDER_GENERATE_HLS_PREVIEW = new MetadataSystemAction(
        "render.generate_hls_preview",
        "Generate HLS Preview",
        "Generate HLS preview segments for media playback",
        "1.0.0",
        "schema://render-hls-preview-input",
        "schema://render-hls-preview-output",
        Set.of("render.create", "media.read", "media.write"),
        Duration.ofMinutes(10),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.RENDER
    );

    // ===== MEDIA Actions =====

    /**
     * Generate proxy for media.
     */
    public static final MetadataSystemAction MEDIA_GENERATE_PROXY = new MetadataSystemAction(
        "media.generate_proxy",
        "Generate Proxy",
        "Generate a proxy version of media for preview",
        "1.0.0",
        "schema://media-generate-proxy-input",
        "schema://media-generate-proxy-output",
        Set.of("media.read", "media.write"),
        Duration.ofMinutes(5),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.MEDIA
    );

    /**
     * Generate thumbnail for media.
     */
    public static final MetadataSystemAction MEDIA_GENERATE_THUMBNAIL = new MetadataSystemAction(
        "media.generate_thumbnail",
        "Generate Thumbnail",
        "Generate thumbnail image for media asset",
        "1.0.0",
        "schema://media-generate-thumbnail-input",
        "schema://media-generate-thumbnail-output",
        Set.of("media.read", "media.write"),
        Duration.ofMinutes(2),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.MEDIA
    );

    /**
     * Transcribe media audio to text.
     */
    public static final MetadataSystemAction MEDIA_TRANSCRIBE = new MetadataSystemAction(
        "media.transcribe",
        "Transcribe Media",
        "Transcribe audio content to text using AI",
        "1.0.0",
        "schema://media-transcribe-input",
        "schema://media-transcribe-output",
        Set.of("media.read", "ai.transcribe"),
        Duration.ofMinutes(15),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.MEDIA
    );

    /**
     * Extract audio from media.
     */
    public static final MetadataSystemAction MEDIA_EXTRACT_AUDIO = new MetadataSystemAction(
        "media.extract_audio",
        "Extract Audio",
        "Extract audio track from video media",
        "1.0.0",
        "schema://media-extract-audio-input",
        "schema://media-extract-audio-output",
        Set.of("media.read", "media.write"),
        Duration.ofMinutes(5),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.MEDIA
    );

    // ===== ARTIFACT Actions =====

    /**
     * Export artifact to external storage.
     */
    public static final MetadataSystemAction ARTIFACT_EXPORT = new MetadataSystemAction(
        "artifact.export",
        "Export Artifact",
        "Export artifact to external storage or delivery system",
        "1.0.0",
        "schema://artifact-export-input",
        "schema://artifact-export-output",
        Set.of("artifact.read", "artifact.export"),
        Duration.ofMinutes(10),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.ARTIFACT
    );

    /**
     * Tag artifact with metadata.
     */
    public static final MetadataSystemAction ARTIFACT_TAG = new MetadataSystemAction(
        "artifact.tag",
        "Tag Artifact",
        "Add metadata tags to artifact",
        "1.0.0",
        "schema://artifact-tag-input",
        "schema://artifact-tag-output",
        Set.of("artifact.read", "artifact.write"),
        Duration.ofSeconds(30),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.ARTIFACT
    );

    // ===== REVIEW Actions =====

    /**
     * Create review link for asset.
     */
    public static final MetadataSystemAction REVIEW_CREATE_LINK = new MetadataSystemAction(
        "review.create_link",
        "Create Review Link",
        "Create a shareable review link for asset approval",
        "1.0.0",
        "schema://review-create-link-input",
        "schema://review-create-link-output",
        Set.of("review.create", "media.read"),
        Duration.ofSeconds(30),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.REVIEW
    );

    /**
     * Request approval for asset.
     */
    public static final MetadataSystemAction REVIEW_REQUEST_APPROVAL = new MetadataSystemAction(
        "review.request_approval",
        "Request Approval",
        "Request approval for asset from reviewers",
        "1.0.0",
        "schema://review-request-approval-input",
        "schema://review-request-approval-output",
        Set.of("review.create", "notification.send"),
        Duration.ofSeconds(30),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.REVIEW
    );

    // ===== NOTIFICATION Actions =====

    /**
     * Send notification to user.
     */
    public static final MetadataSystemAction NOTIFICATION_SEND = new MetadataSystemAction(
        "notification.send",
        "Send Notification",
        "Send notification to user or group",
        "1.0.0",
        "schema://notification-send-input",
        "schema://notification-send-output",
        Set.of("notification.send"),
        Duration.ofSeconds(30),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.NOTIFICATION
    );

    // ===== WEBHOOK Actions =====

    /**
     * Send webhook to external endpoint.
     */
    public static final MetadataSystemAction WEBHOOK_SEND = new MetadataSystemAction(
        "webhook.send",
        "Send Webhook",
        "Send webhook event to external endpoint",
        "1.0.0",
        "schema://webhook-send-input",
        "schema://webhook-send-output",
        Set.of("webhook.send"),
        Duration.ofSeconds(30),
        true,
        CapabilityStability.STABLE,
        SystemActionCategory.WEBHOOK
    );

    // ===== Catalog Methods =====

    /**
     * Get all built-in system actions.
     *
     * @return immutable list of all built-in actions
     */
    public static List<SystemAction> all() {
        return List.of(
            RENDER_CREATE_JOB,
            RENDER_GENERATE_HLS_PREVIEW,
            MEDIA_GENERATE_PROXY,
            MEDIA_GENERATE_THUMBNAIL,
            MEDIA_TRANSCRIBE,
            MEDIA_EXTRACT_AUDIO,
            ARTIFACT_EXPORT,
            ARTIFACT_TAG,
            REVIEW_CREATE_LINK,
            REVIEW_REQUEST_APPROVAL,
            NOTIFICATION_SEND,
            WEBHOOK_SEND
        );
    }

    /**
     * Register all built-in actions into the given registry.
     *
     * @param registry the registry to register actions into
     */
    public static void registerInto(SystemActionRegistry registry) {
        for (SystemAction action : all()) {
            registry.register(action);
        }
    }

    /**
     * Find a built-in action by key.
     *
     * @param actionKey the action key
     * @return the action, or null if not found
     */
    public static SystemAction findByKey(String actionKey) {
        return all().stream()
            .filter(a -> a.actionKey().equals(actionKey))
            .findFirst()
            .orElse(null);
    }
}
