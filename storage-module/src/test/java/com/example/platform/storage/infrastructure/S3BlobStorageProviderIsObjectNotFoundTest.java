package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.Method;

/**
 * Tests for S3BlobStorageProvider's private isObjectNotFound method, invoked via reflection.
 * This method is the core of the fix for returning Optional.empty() on 404 NoSuchKey.
 */
class S3BlobStorageProviderIsObjectNotFoundTest {

    private final S3BlobStorageProvider provider = createProvider();

    private static S3BlobStorageProvider createProvider() {
        try {
            StorageS3Properties props = new StorageS3Properties();
            props.setDefaultBucket("test-bucket");
            props.setRegion("us-east-1");
            props.setPathStyleAccess(true);
            props.setChunkedEncodingEnabled(false);
            return new S3BlobStorageProvider(props);
        } catch (Exception e) {
            // If constructor fails due to SDK issues, create via reflection without constructor
            try {
                java.lang.reflect.Constructor<S3BlobStorageProvider> ctor =
                        S3BlobStorageProvider.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (Exception ex) {
                throw new RuntimeException("Cannot create provider", ex);
            }
        }
    }

    private boolean invokeIsObjectNotFound(S3Exception ex) throws Exception {
        Method method = S3BlobStorageProvider.class.getDeclaredMethod("isObjectNotFound", S3Exception.class);
        method.setAccessible(true);
        return (boolean) method.invoke(provider, ex);
    }

    private static S3Exception s3Exception(int statusCode, String errorCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(statusCode)
                .message("error")
                .awsErrorDetails(
                        software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                                .errorCode(errorCode)
                                .errorMessage("error detail")
                                .build())
                .build();
    }

    private static S3Exception s3Exception(int statusCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(statusCode)
                .message("error")
                .build();
    }

    // === 404 NoSuchKey / NotFound → true ===

    @Test
    void returnsTrueFor404NoSuchKey() throws Exception {
        assertTrue(invokeIsObjectNotFound(s3Exception(404, "NoSuchKey")),
                "404 NoSuchKey should be treated as object not found");
    }

    @Test
    void returnsTrueFor404NotFound() throws Exception {
        assertTrue(invokeIsObjectNotFound(s3Exception(404, "NotFound")),
                "404 NotFound should be treated as object not found");
    }

    // === Non-404 status codes → false ===

    @Test
    void returnsFalseFor403AccessDenied() throws Exception {
        assertFalse(invokeIsObjectNotFound(s3Exception(403, "AccessDenied")),
                "403 should NOT be treated as object not found");
    }

    @Test
    void returnsFalseFor500InternalError() throws Exception {
        assertFalse(invokeIsObjectNotFound(s3Exception(500, "InternalError")),
                "500 should NOT be treated as object not found");
    }

    @Test
    void returnsFalseFor400BadRequest() throws Exception {
        assertFalse(invokeIsObjectNotFound(s3Exception(400, "InvalidRequest")),
                "400 should NOT be treated as object not found");
    }

    // === 404 with null errorCode → false ===

    @Test
    void returnsFalseFor404WithNullErrorCode() throws Exception {
        assertFalse(invokeIsObjectNotFound(s3Exception(404)),
                "404 with null errorCode should NOT be treated as object not found");
    }

    @Test
    void returnsFalseFor404WithUnrecognizedErrorCode() throws Exception {
        assertFalse(invokeIsObjectNotFound(s3Exception(404, "SomeUnknownError")),
                "404 with unknown errorCode should NOT be treated as object not found");
    }
}
