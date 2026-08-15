package com.example.platform.fonttext.execution;

import java.util.Objects;

/**
 * ROADMAP_19 (C46/R6): versioned provider-neutral layout execution input.
 * NEVER authored Timeline text state; NEVER in Timeline hash; RenderPlan pins it (#20).
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextLayoutAlgorithmProfile {

    private final String unicodeDataProfile;
    private final String bidiProfile;
    private final String lineBreakProfile;
    private final String graphemeProfile;
    private final String textLayoutContractVersion;

    public TextLayoutAlgorithmProfile(String unicodeDataProfile, String bidiProfile, String lineBreakProfile,
                                      String graphemeProfile, String textLayoutContractVersion) {
        this.unicodeDataProfile = Objects.requireNonNull(unicodeDataProfile, "unicodeDataProfile");
        this.bidiProfile = Objects.requireNonNull(bidiProfile, "bidiProfile");
        this.lineBreakProfile = Objects.requireNonNull(lineBreakProfile, "lineBreakProfile");
        this.graphemeProfile = Objects.requireNonNull(graphemeProfile, "graphemeProfile");
        this.textLayoutContractVersion = Objects.requireNonNull(textLayoutContractVersion, "textLayoutContractVersion");
    }

    public String unicodeDataProfile() { return unicodeDataProfile; }
    public String bidiProfile() { return bidiProfile; }
    public String lineBreakProfile() { return lineBreakProfile; }
    public String graphemeProfile() { return graphemeProfile; }
    public String textLayoutContractVersion() { return textLayoutContractVersion; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextLayoutAlgorithmProfile p)) return false;
        return unicodeDataProfile.equals(p.unicodeDataProfile) && bidiProfile.equals(p.bidiProfile)
                && lineBreakProfile.equals(p.lineBreakProfile) && graphemeProfile.equals(p.graphemeProfile)
                && textLayoutContractVersion.equals(p.textLayoutContractVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unicodeDataProfile, bidiProfile, lineBreakProfile, graphemeProfile, textLayoutContractVersion);
    }
}
