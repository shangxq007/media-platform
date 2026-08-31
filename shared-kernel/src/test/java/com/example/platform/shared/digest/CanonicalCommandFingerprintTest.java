package com.example.platform.shared.digest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CanonicalCommandFingerprintTest {

    @Test
    void delimiterCollisionInputsHaveDifferentPreimagesAndHashes() {
        var left = command("a|b", "c", "main");
        var right = command("a", "b|c", "main");
        assertFalse(Arrays.equals(left.framedBytes(), right.framedBytes()));
        assertNotEquals(left.sha256Hex(), right.sha256Hex());
    }

    @Test
    void nullAndEmptyRemainDistinct() {
        var absent = base().nullable("authorizationVersion", null);
        var empty = base().nullable("authorizationVersion", "");
        assertFalse(Arrays.equals(absent.framedBytes(), empty.framedBytes()));
        assertNotEquals(absent.sha256Hex(), empty.sha256Hex());
    }

    @Test
    void controlUnicodeDelimiterAndLongValuesRemainInjective() {
        String adversarial = "line\nzero\0雪|" + "x".repeat(4096);
        assertNotEquals(command("tenant", adversarial, "main").sha256Hex(),
                command("tenant", adversarial + "x", "main").sha256Hex());
    }

    @Test
    void exactFieldsAreDeterministic() {
        assertArrayEquals(command("tenant", "project", "main").framedBytes(),
                command("tenant", "project", "main").framedBytes());
    }

    @Test
    void everyRequiredIdentityFieldChangesHash() {
        String baseline = command("tenant", "project", "main").sha256Hex();
        assertNotEquals(baseline, command("tenant-2", "project", "main").sha256Hex());
        assertNotEquals(baseline, command("tenant", "project-2", "main").sha256Hex());
        assertNotEquals(baseline, command("tenant", "project", "feature").sha256Hex());
        assertNotEquals(baseline, CanonicalCommandFingerprint.builder("OTHER_DOMAIN")
                .required("tenantId", "tenant").required("projectId", "project")
                .required("targetRefId", "main").sha256Hex());
    }

    @Test
    void fieldTagsAndOrderParticipate() {
        String baseline = base().required("targetRefId", "main").sha256Hex();
        assertNotEquals(baseline, base().required("targetIdentity", "main").sha256Hex());
        assertNotEquals(baseline, CanonicalCommandFingerprint.builder("OPERATION_PLAN")
                .required("projectId", "project").required("tenantId", "tenant")
                .required("targetRefId", "main").sha256Hex());
    }

    @Test
    void duplicateOrMissingRequiredFieldsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> base().required("tenantId", "again"));
        assertThrows(IllegalArgumentException.class,
                () -> base().required("targetRefId", null));
    }

    private static CanonicalCommandFingerprint.Builder command(
            String tenant, String project, String target) {
        return CanonicalCommandFingerprint.builder("OPERATION_PLAN")
                .required("tenantId", tenant)
                .required("projectId", project)
                .required("targetRefId", target);
    }

    private static CanonicalCommandFingerprint.Builder base() {
        return CanonicalCommandFingerprint.builder("OPERATION_PLAN")
                .required("tenantId", "tenant")
                .required("projectId", "project");
    }
}
