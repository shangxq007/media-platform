package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C39): provider-neutral technical discovery input — validated
 * execution fonts only. NOT Timeline canonical authority; NOT historical state.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class ValidatedFontCatalogSnapshot {

    public record Entry(ValidatedFontExecutionReference reference, FontFaceManifest manifest) {}

    private final List<Entry> entries;

    public ValidatedFontCatalogSnapshot(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entries() { return entries; }

    @Override
    public boolean equals(Object o) { return o instanceof ValidatedFontCatalogSnapshot s && entries.equals(s.entries); }

    @Override
    public int hashCode() { return entries.hashCode(); }
}
