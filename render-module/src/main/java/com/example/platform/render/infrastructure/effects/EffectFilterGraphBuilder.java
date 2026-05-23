package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.infrastructure.EffectMappingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds FFmpeg {@code -vf} filter chains from standardized effect keys.
 */
@Component
public class EffectFilterGraphBuilder {

    private final EffectMappingService effectMapping;

    public EffectFilterGraphBuilder(EffectMappingService effectMapping) {
        this.effectMapping = effectMapping;
    }

    public Optional<String> buildVideoFilterChain(List<TimelineClipEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return Optional.empty();
        }
        List<String> filters = new ArrayList<>();
        for (TimelineClipEffect effect : effects) {
            if (effect == null || effect.effectKey() == null) {
                continue;
            }
            String filter = toFfmpegFilter(effect.effectKey(), effect.parameters());
            if (filter != null && !filter.isBlank()) {
                filters.add(filter);
            }
        }
        if (filters.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(",", filters));
    }

    private String toFfmpegFilter(String effectKey, Map<String, Object> params) {
        if (!effectMapping.getDescriptor(effectKey).isPresent()) {
            return null;
        }
        Map<String, Object> p = params != null ? params : Map.of();
        return switch (effectKey) {
            case "video.fade_in" -> "fade=t=in:st=0:d=" + num(p, "duration", 1.0);
            case "video.fade_out" -> "fade=t=out:st=0:d=" + num(p, "duration", 1.0);
            case "video.blur" -> "boxblur=" + num(p, "radius", 2.0) + ":" + num(p, "radius", 2.0);
            case "video.brightness" -> "eq=brightness=" + (num(p, "value", 1.2) - 1.0);
            case "video.contrast" -> "eq=contrast=" + num(p, "value", 1.1);
            case "video.grayscale" -> "hue=s=0";
            case "video.sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131";
            case "video.sharpen" -> "unsharp=5:5:" + num(p, "amount", 1.5) + ":5:5:0.0";
            case "text.subtitle_burn_in" -> subtitleFilter(p);
            case "video.particle_overlay" -> particleOverlayHint(p);
            default -> null;
        };
    }

    private static String subtitleFilter(Map<String, Object> params) {
        String text = str(params, "text", "Subtitle");
        String position = str(params, "position", "bottom");
        int fontSize = (int) num(params, "fontSize", 24);
        String escaped = text.replace("'", "\\'").replace(":", "\\:");
        String y = switch (position.toLowerCase()) {
            case "top" -> "50";
            case "center" -> "h/2";
            default -> "h-th-50";
        };
        return "drawtext=text='" + escaped + "':fontsize=" + fontSize + ":fontcolor=white:x=(w-tw)/2:y=" + y;
    }

    private static double num(Map<String, Object> params, String key, double defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * PopcornFX overlays use a second input; OFX/JavaCV compositor applies {@code assetPath}.
     */
    private static String particleOverlayHint(Map<String, Object> params) {
        String path = str(params, "assetPath", "");
        if (path.isBlank()) {
            return null;
        }
        double opacity = num(params, "opacity", 1.0);
        return "null[popcornfx-overlay=" + path + ";opacity=" + opacity + "]";
    }

    private static String str(Map<String, Object> params, String key, String defaultValue) {
        Object v = params.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
