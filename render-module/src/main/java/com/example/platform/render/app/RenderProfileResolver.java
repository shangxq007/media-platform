package com.example.platform.render.app;

import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import com.example.platform.render.infrastructure.natron.NatronRenderProviderProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves render profile from timeline effects — e.g. upgrades to {@code natron_poc_*} when
 * Natron effects are present and the Natron provider is enabled.
 */
@Component
public class RenderProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(RenderProfileResolver.class);

    public static final String NATRON_POC_1080P = "natron_poc_1080p";
    public static final String NATRON_POC_720P = "natron_poc_720p";
    public static final String SHOTSTACK_SOCIAL_1080P = "shotstack_social_1080p";
    public static final String SHOTSTACK_SOCIAL_720P = "shotstack_social_720p";
    public static final String REMOTION_1080P = "remotion_1080p";
    public static final String REMOTION_SOCIAL = "remotion_social";
    public static final String BLENDER_1080P = "blender_1080p";
    public static final String BLENDER_4K = "blender_4k";

    private final EffectProviderRouter effectProviderRouter;
    private final TimelineScriptParser timelineScriptParser;
    private final RenderProviderRegistry providerRegistry;
    private final Optional<NatronRenderProviderProperties> natronProperties;

    public RenderProfileResolver(
            EffectProviderRouter effectProviderRouter,
            TimelineScriptParser timelineScriptParser,
            RenderProviderRegistry providerRegistry,
            @Autowired(required = false) NatronRenderProviderProperties natronProperties) {
        this.effectProviderRouter = effectProviderRouter;
        this.timelineScriptParser = timelineScriptParser;
        this.providerRegistry = providerRegistry;
        this.natronProperties = Optional.ofNullable(natronProperties);
    }

    /**
     * @param requestedProfile profile from API / job row (may be default)
     * @param effectKeys       effect keys extracted from timeline script
     * @param timelineScript   optional full script for resolution hints
     */
    public String resolve(String requestedProfile, List<String> effectKeys, String timelineScript) {
        String base = requestedProfile == null || requestedProfile.isBlank()
                ? "default_1080p"
                : requestedProfile;

        if (base.startsWith("natron_") || base.startsWith("shotstack_")
                || base.startsWith("remotion_") || base.startsWith("blender_")) {
            return base;
        }

        if (effectKeys != null && effectKeys.contains("video.remotion_template")
                && providerRegistry.getProvider("remotion").isPresent()) {
            log.info("Render profile auto-selected for Remotion: {} -> {}", base, REMOTION_1080P);
            return REMOTION_1080P;
        }

        if (effectKeys != null && effectKeys.contains("video.blender_scene")
                && providerRegistry.getProvider("blender").isPresent()) {
            log.info("Render profile auto-selected for Blender: {} -> {}", base, BLENDER_1080P);
            return BLENDER_1080P;
        }

        if (effectKeys != null && effectKeys.contains("video.shotstack_template")
                && providerRegistry.getProvider("shotstack").isPresent()) {
            String resolved = mapToShotstackProfile(base, timelineScript);
            log.info("Render profile auto-selected for Shotstack: {} -> {}", base, resolved);
            return resolved;
        }

        if (!isNatronRoutingAvailable()
                || natronProperties.map(p -> !p.isAutoSelectProfile()).orElse(true)) {
            return base;
        }

        if (effectKeys == null || effectKeys.isEmpty()) {
            return base;
        }

        if (!effectProviderRouter.requiresNatronPipeline(effectKeys, null)) {
            return base;
        }

        String resolved = mapToNatronProfile(base, timelineScript);
        if (!resolved.equals(base)) {
            log.info("Render profile auto-selected: {} -> {} (effects={})", base, resolved, effectKeys);
        }
        return resolved;
    }

    private boolean isNatronRoutingAvailable() {
        if (natronProperties.isEmpty() || !natronProperties.get().isEnabled()) {
            return false;
        }
        return providerRegistry.getProvider("natron").isPresent();
    }

    private String mapToShotstackProfile(String requestedProfile, String timelineScript) {
        if (requestedProfile.contains("720") || requestedProfile.contains("480")
                || requestedProfile.contains("mobile")) {
            return SHOTSTACK_SOCIAL_720P;
        }
        Optional<TimelineSpec> spec = timelineScriptParser.parse(
                timelineScript != null ? timelineScript : "");
        if (spec.isPresent()) {
            TimelineOutputSpec output = spec.get().outputSpec();
            if (output != null && output.height() > 0 && output.height() <= 720) {
                return SHOTSTACK_SOCIAL_720P;
            }
        }
        return SHOTSTACK_SOCIAL_1080P;
    }

    private String mapToNatronProfile(String requestedProfile, String timelineScript) {
        if (requestedProfile.contains("720") || requestedProfile.contains("480")
                || requestedProfile.contains("mobile")) {
            return NATRON_POC_720P;
        }
        Optional<TimelineSpec> spec = timelineScriptParser.parse(
                timelineScript != null ? timelineScript : "");
        if (spec.isPresent()) {
            TimelineOutputSpec output = spec.get().outputSpec();
            if (output != null && output.height() > 0 && output.height() <= 720) {
                return NATRON_POC_720P;
            }
        }
        return NATRON_POC_1080P;
    }

    public Set<String> natronEffectKeys() {
        return natronProperties
                .map(p -> Set.copyOf(p.getSupportedEffectKeys()))
                .orElse(Set.of("video.natron_vignette"));
    }
}
