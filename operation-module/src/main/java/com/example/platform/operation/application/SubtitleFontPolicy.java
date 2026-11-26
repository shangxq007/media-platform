package com.example.platform.operation.application;

import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.timeline.canonical.TimelineMetadata;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

/** Authoring availability and defaults. Historical layout uses the installed pinned identity instead. */
@Service
@NamedInterface("application")
public final class SubtitleFontPolicy {
    private final List<SubtitleBlockLayout.Profile> selectable;
    private final String defaultProfile;
    private final Map<String, String> byLanguage;

    public SubtitleFontPolicy(
            @Value("${media.subtitle-fonts.available:noto-sc-color-emoji-v1,noto-serif-sc-color-emoji-v1}") String available,
            @Value("${media.subtitle-fonts.default:noto-sc-color-emoji-v1}") String defaultProfile,
            @Value("${media.subtitle-fonts.by-language:}") String languageDefaults) {
        var ids = new LinkedHashSet<String>();
        for (String id : available.split(",", -1)) {
            if (id.isBlank() || !ids.add(id.trim())) throw new IllegalArgumentException("Invalid subtitle font catalog");
        }
        this.selectable = ids.stream().map(SubtitleBlockLayout::profile).toList();
        this.defaultProfile = requireSelectable(defaultProfile);
        var overrides = new LinkedHashMap<String, String>();
        if (!languageDefaults.isBlank()) for (String entry : languageDefaults.split(",", -1)) {
            String[] key = entry.split("=", -1);
            if (key.length != 2) throw new IllegalArgumentException("Expected subtitle font override language=profile");
            String tag = languageKey(key[0]);
            if (overrides.putIfAbsent(tag, requireSelectable(key[1].trim())) != null) {
                throw new IllegalArgumentException("Duplicate subtitle track language override");
            }
        }
        this.byLanguage = Map.copyOf(overrides);
    }

    public List<SubtitleBlockLayout.Profile> selectable() { return selectable; }

    public String resolve(String explicit, String language, String saved) {
        if (explicit != null && !explicit.equals(saved)) return requireSelectable(explicit);
        if (saved != null) return SubtitleBlockLayout.resolveProfile(saved);
        return language == null ? defaultProfile : byLanguage.getOrDefault(languageKey(language), defaultProfile);
    }

    String requireSelectable(String id) {
        if (selectable.stream().noneMatch(profile -> profile.id().equals(id))) {
            throw new IllegalArgumentException("Subtitle font profile is unavailable for new selection");
        }
        return id;
    }

    private static String languageKey(String tag) {
        return TimelineMetadata.checkedSubtitleLanguage(tag).toLowerCase(Locale.ROOT);
    }
}
