package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CIC1/CIC3): canonical sealed color description root. Exactly one
 * variant per SourceVisualDescription; no nullable god object; no sibling
 * profile field; no raw provider strings; no ArtifactId/path/URI/ICC bytes.
 */
public sealed interface ColorDescription permits
        ColorDescription.ParametricColorDescription,
        ColorDescription.ProfileBasedColorDescription {

    record ParametricColorDescription(
            ColorPrimaries primaries,
            TransferCharacteristic transfer,
            MatrixCoefficients matrix,
            SignalRange range) implements ColorDescription {

        public ParametricColorDescription {
            if (primaries == null || transfer == null || matrix == null || range == null) {
                throw new IllegalArgumentException("parametric color fields required (use typed UNSPECIFIED)");
            }
        }
    }

    record ProfileBasedColorDescription(
            ProfileFormat profileFormat,
            ColorProfileContentDigest profileContentDigest) implements ColorDescription {

        public ProfileBasedColorDescription {
            if (profileFormat == null) {
                throw new IllegalArgumentException("profile format is required (CIC3)");
            }
            if (profileContentDigest == null) {
                throw new IllegalArgumentException("profile content digest is required");
            }
        }
    }
}
