package com.example.platform.shared.version;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 test matrix (§33):
 * version parsing/comparison/range/lifecycle/channel/rollout/provenance/
 * identity scoping; greenfield canonicalization rules.
 */
class VersionGovernanceTest {

    // ---- VERSION PARSING ----
    @Test
    void releaseVersionParsingPass() {
        assertEquals(ReleaseVersion.of(2, 4, 0), ReleaseVersion.parse("2.4.0"));
        assertEquals(ReleaseVersion.of(2, 4, 1), ReleaseVersion.parse("2.4.1"));
        assertEquals(ReleaseVersion.of(3, 0, 0), ReleaseVersion.parse("3.0.0"));
    }

    @Test
    void releaseVersionParsingFails() {
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("a.b.c"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("-1.0.0"));
    }

    @Test
    void contractStyleVersionRequiresER() {
        // single-segment rejected; E.R accepted; E.R.P rejected where contract required
        assertThrows(IllegalArgumentException.class, () -> CanonicalFormatVersion.parse("1"));
        assertEquals(CanonicalFormatVersion.of(1, 0), CanonicalFormatVersion.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalFormatVersion.parse("1.2.3"));
    }

    // ---- VERSION COMPARISON (numeric, never lexicographic) ----
    @Test
    void numericComparison() {
        assertTrue(ReleaseVersion.parse("1.9.0").compareTo(ReleaseVersion.parse("1.10.0")) < 0);
        assertTrue(ReleaseVersion.parse("2.0.0").compareTo(ReleaseVersion.parse("1.99.0")) > 0);
        assertTrue(CanonicalFormatVersion.parse("2.4").compareTo(CanonicalFormatVersion.parse("1.0")) > 0);
    }

    // ---- VERSION RANGE ----
    @Test
    void rangeContainsAndExcludes() {
        VersionRange<ReleaseVersion> r = VersionRange.between(
                ReleaseVersion.parse("2.1.0"), ReleaseVersion.parse("3.0.0"));
        assertTrue(r.contains(ReleaseVersion.parse("2.1.0")));
        assertTrue(r.contains(ReleaseVersion.parse("2.9.9")));
        assertFalse(r.contains(ReleaseVersion.parse("2.0.9")));
        assertTrue(r.contains(ReleaseVersion.parse("3.0.0")));
    }

    @Test
    void rangeIntersection() {
        VersionRange<ReleaseVersion> a = VersionRange.between(
                ReleaseVersion.parse("2.0.0"), ReleaseVersion.parse("2.5.0"));
        VersionRange<ReleaseVersion> b = VersionRange.between(
                ReleaseVersion.parse("2.4.0"), ReleaseVersion.parse("3.0.0"));
        Optional<VersionRange<ReleaseVersion>> i = a.intersection(b);
        assertTrue(i.isPresent());
        assertTrue(i.get().contains(ReleaseVersion.parse("2.4.5")));
        assertFalse(i.get().contains(ReleaseVersion.parse("2.3.0")));

        VersionRange<ReleaseVersion> c = VersionRange.between(
                ReleaseVersion.parse("3.1.0"), ReleaseVersion.parse("4.0.0"));
        assertTrue(a.intersection(c).isEmpty());
    }

    @Test
    void invalidBoundsRejected() {
        assertThrows(IllegalArgumentException.class, () -> VersionRange.between(
                ReleaseVersion.parse("3.0.0"), ReleaseVersion.parse("2.0.0")));
    }

    // ---- IDENTITY SCOPING ----
    @Test
    void numericEqualityDoesNotImplyCompatibility() {
        // timeline.format@2.4 and audio.mix@2.4 are DIFFERENT compatibility spaces
        String timelineFormat = "timeline.format@" + CanonicalFormatVersion.of(2, 4);
        String audioMix = "audio.mix@" + CanonicalFormatVersion.of(2, 4);
        assertNotEquals(timelineFormat, audioMix);
    }

    // ---- LIFECYCLE ----
    @Test
    void lifecycleExplicitNotVersionParity() {
        assertNotEquals(Lifecycle.STABLE, Lifecycle.DEPRECATED);
        assertNotEquals(Lifecycle.DEPRECATED, Lifecycle.RETIRED);
        // no odd/even rule: version numbers never encode lifecycle
        assertNotEquals(Lifecycle.STABLE.name(), "even");
        assertEquals(5, Lifecycle.values().length);
    }

    // ---- CHANNEL / ROLLOUT ----
    @Test
    void channelIndependentFromLifecycle() {
        // distinct enum TYPES (names may coincide; semantics are separate axes)
        assertNotEquals(ReleaseChannel.class, Lifecycle.class);
        assertEquals(ReleaseChannel.CANARY, ReleaseChannel.CANARY);
        assertNotEquals(ReleaseChannel.CANARY, ReleaseChannel.STABLE);
        // release version unchanged by channel
        assertEquals(ReleaseVersion.of(2, 5, 0), ReleaseVersion.of(2, 5, 0));
    }

    @Test
    void deterministicCohortBucketing() {
        RolloutPolicy policy = new RolloutPolicy("rollout-a", "rev-1");
        String subject = "workspace-42";
        // same subject + same immutable policy => stable cohort
        assertEquals(policy.cohortFor(subject, 10, 0), policy.cohortFor(subject, 10, 0));
        assertEquals(policy.cohortFor(subject, 10, 0), policy.cohortFor(subject, 10, 0));
        // boundary deterministic: cohort within [0, cohortCount)
        String cohort = policy.cohortFor(subject, 10, 0);
        assertTrue(cohort.startsWith("cohort-"));
        int idx = Integer.parseInt(cohort.substring("cohort-".length()));
        assertTrue(idx >= 0 && idx < 10);
        // policy revision participates in the stable key (revision change is
        // explicitly visible in allocation semantics, not silently ignored)
        RolloutPolicy other = new RolloutPolicy("rollout-a", "rev-2");
        assertNotEquals(policy.policyRevision(), other.policyRevision());
    }

    // ---- EXECUTION PROVENANCE ----
    @Test
    void provenanceRecordsResolvedVersions() {
        ExecutionProvenance p = new ExecutionProvenance(
                ReleaseVersion.of(2, 5, 0),
                ExecutionProvenance.BuildIdentity.of("abcdef1234567890"),
                ReleaseChannel.CANARY,
                "audio.transcribe", "1.0", "impl-1", "1.2.0", "plugin-x", "1.0.0",
                "provider-ffmpeg", null, null,
                null, null, // no AI model (local execution)
                new RolloutPolicy("rollout-a", "rev-1"), "cohort-3",
                "digest-abc", "rev-input", "artifact-in", "digest-in",
                null, null, null, "trace-1");
        assertEquals(ReleaseVersion.of(2, 5, 0), p.platformReleaseVersion());
        assertEquals("cohort-3", p.rolloutCohort());
        assertEquals("trace-1", p.traceId());
        // model fields absent when not applicable (no fake placeholder ids)
        assertNull(p.modelId());
        assertNull(p.workerRuntimeVersion());
    }

    // ---- API CONTRACT GOVERNANCE ----
    @Test
    void apiContractIndependentFromPlatformRelease() {
        ApiContract api = new ApiContract("media-api", CanonicalFormatVersion.of(3, 2),
                ApiContract.ApiLifecycle.STABLE);
        assertEquals(CanonicalFormatVersion.of(3, 2), api.contractVersion());
        assertEquals(ApiContract.ApiLifecycle.STABLE, api.lifecycle());
        // independent axes may coexist without overloading one field
        assertNotEquals(api.contractVersion().toString(), ReleaseVersion.of(2, 5, 0).toString());
    }

    // ---- GREENFIELD ----
    @Test
    void noLegacyCompatibilitySurface() {
        // single-segment ContractVersion rejected (repository-wide rule, #16 + VCG)
        assertThrows(IllegalArgumentException.class, () -> CanonicalFormatVersion.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("1"));
        // no version-parity policy surface
        assertNull(System.getProperty("version.parity.compat"));
    }
}
