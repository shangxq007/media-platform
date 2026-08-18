package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.typography.VariationCoordinate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C24/C27/C44): exact historical instance — validated execution
 * content + face + exact variation coordinates. NO unresolved AUTO axis.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class ResolvedFontInstance {

    private final ValidatedFontExecutionReference executionReference;
    private final List<VariationCoordinate> variationCoordinates; // sorted by tag

    @com.fasterxml.jackson.annotation.JsonCreator
public ResolvedFontInstance(@com.fasterxml.jackson.annotation.JsonProperty("executionReference") ValidatedFontExecutionReference executionReference, @com.fasterxml.jackson.annotation.JsonProperty("variationCoordinates") List<VariationCoordinate> variationCoordinates) {
        this.executionReference = Objects.requireNonNull(executionReference, "executionReference");
        List<VariationCoordinate> axes = new ArrayList<>(variationCoordinates);
        axes.sort(null);
        this.variationCoordinates = Collections.unmodifiableList(axes);
    }

    public ValidatedFontExecutionReference executionReference() { return executionReference; }
    public List<VariationCoordinate> variationCoordinates() { return variationCoordinates; }
    public FontContentDigest validatedDigest() { return executionReference.validatedExecutionContentDigest(); }
    public FaceIndex faceIndex() { return executionReference.faceIndex(); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResolvedFontInstance i)) return false;
        return executionReference.equals(i.executionReference) && variationCoordinates.equals(i.variationCoordinates);
    }

    @Override
    public int hashCode() { return Objects.hash(executionReference, variationCoordinates); }
}
