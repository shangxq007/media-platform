package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata descriptor for an Artifact — a mutable bag of descriptive metadata
 * that does NOT affect the Artifact's content identity.
 *
 * <p>The descriptor holds user-supplied metadata (title, description, tags, custom fields)
 * while the Artifact record holds the immutable content-identity fields.
 */
public final class ArtifactDescriptor implements Serializable {

    private final String title;
    private final String description;
    private final Map<String, String> tags;
    private final Map<String, String> customFields;

    private ArtifactDescriptor(String title, String description, Map<String, String> tags, Map<String, String> customFields) {
        this.title = title;
        this.description = description;
        this.tags = tags != null ? Map.copyOf(tags) : Map.of();
        this.customFields = customFields != null ? Map.copyOf(customFields) : Map.of();
    }

    public static ArtifactDescriptor empty() {
        return new ArtifactDescriptor(null, null, Map.of(), Map.of());
    }

    public static ArtifactDescriptor of(String title, String description) {
        return new ArtifactDescriptor(title, description, Map.of(), Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() { return title; }
    public String description() { return description; }
    public Map<String, String> tags() { return tags; }
    public Map<String, String> customFields() { return customFields; }

    public Builder toBuilder() {
        return builder()
                .title(this.title)
                .description(this.description)
                .tags(new LinkedHashMap<>(this.tags))
                .customFields(new LinkedHashMap<>(this.customFields));
    }

    public static final class Builder {
        private String title;
        private String description;
        private Map<String, String> tags = new LinkedHashMap<>();
        private Map<String, String> customFields = new LinkedHashMap<>();

        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder tags(Map<String, String> tags) { if (tags != null) this.tags = tags; return this; }
        public Builder customFields(Map<String, String> cf) { if (cf != null) this.customFields = cf; return this; }
        public Builder tag(String key, String value) { this.tags.put(key, value); return this; }
        public Builder customField(String key, String value) { this.customFields.put(key, value); return this; }

        public ArtifactDescriptor build() {
            return new ArtifactDescriptor(title, description, tags, customFields);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArtifactDescriptor that)) return false;
        return Objects.equals(title, that.title) &&
                Objects.equals(description, that.description) &&
                tags.equals(that.tags) &&
                customFields.equals(that.customFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, tags, customFields);
    }

    /**
     * Canonical serialization: deterministic field ordering via LinkedHashMap.
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder("descriptor{");
        if (title != null) sb.append("title=").append(title).append(',');
        if (description != null) sb.append("description=").append(description).append(',');
        sb.append("tags=").append(tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()).append(',');
        sb.append("custom=").append(customFields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
