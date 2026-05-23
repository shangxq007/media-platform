package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class S3ClientSettingsResolverTest {

    @Test
    void r2ModeAppliesPresets() {
        StorageS3Properties props = new StorageS3Properties();
        props.setCompatibility(S3CompatibilityMode.R2);
        props.setAccountId("abc123");
        props.setAccessKey("key");
        props.setSecretKey("secret");

        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(props);

        assertEquals(S3CompatibilityMode.R2, resolved.compatibilityMode());
        assertEquals("https://abc123.r2.cloudflarestorage.com", resolved.endpoint());
        assertEquals("auto", resolved.region());
        assertTrue(resolved.pathStyleAccess());
        assertFalse(resolved.chunkedEncodingEnabled());
    }

    @Test
    void r2ModeRespectsExplicitEndpointAndRegion() {
        StorageS3Properties props = new StorageS3Properties();
        props.setCompatibility(S3CompatibilityMode.R2);
        props.setEndpoint("https://custom.example.com");
        props.setRegion("eu-west-1");

        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(props);

        assertEquals("https://custom.example.com", resolved.endpoint());
        assertEquals("eu-west-1", resolved.region());
        assertFalse(resolved.chunkedEncodingEnabled());
    }

    @Test
    void genericModeKeepsChunkedEncoding() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEndpoint("http://rustfs:9000");
        props.setRegion("us-east-1");
        props.setPathStyleAccess(true);

        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(props);

        assertEquals(S3CompatibilityMode.GENERIC, resolved.compatibilityMode());
        assertTrue(resolved.chunkedEncodingEnabled());
    }
}
