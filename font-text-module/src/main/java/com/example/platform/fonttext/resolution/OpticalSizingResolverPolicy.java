package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.VariationAxisTag;

/**
 * ROADMAP_19 (C28/R6): provider-neutral AUTO optical-sizing resolution policy
 * port. Resolves AUTO to an exact opsz coordinate BEFORE atomic Timeline apply.
 * No host/browser/mutable defaults.
 */
public interface OpticalSizingResolverPolicy {

    FontRational resolveAutoOpticalSize(FontRational fontSize);
}
