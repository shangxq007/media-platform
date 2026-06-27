package com.example.platform.storage.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Integration test for {@link S3ObjectMaterializer} against a real S3-compatible backend.
 *
 * <p>This test is <b>opt-in</b>: it only runs when the S3 endpoint at
 * {@code http://localhost:9000} is reachable (e.g., RustFS dev profile is up).
 * Run with:
 * <pre>
 * docker compose -f docker-compose.dev.yml --profile s3 up -d
 * ./gradlew :storage-module:test --tests "*S3ObjectMaterializerIntegrationTest"
 * </pre>
 *
 * <p>Uses dev-only credentials (no production secrets).</p>
 */
class S3ObjectMaterializerIntegrationTest {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String REGION = "us-east-1";
    private static final String ACCESS_KEY = "dev-access-key";
    private static final String SECRET_KEY = "dev-secret-key";
    private static final String BUCKET = "r10a-smoke-test";

    @BeforeAll
    static void requireS3Endpoint() {
        boolean reachable = false;
        try (Socket socket = new Socket("localhost", 9000)) {
            reachable = true;
        } catch (IOException ignored) {}
        assumeTrue(reachable, "S3 endpoint not reachable at localhost:9000; skipping integration test");
    }

    private static StorageS3Properties createProperties() {
        StorageS3Properties props = new StorageS3Properties();
        props.setEnabled(true);
        props.setEndpoint(ENDPOINT);
        props.setRegion(REGION);
        props.setAccessKey(ACCESS_KEY);
        props.setSecretKey(SECRET_KEY);
        props.setPathStyleAccess(true);
        props.setDefaultBucket(BUCKET);
        return props;
    }

    private static S3Client createS3Client() {
        StorageS3Properties props = createProperties();
        S3ClientSettingsResolver.Resolved resolved = S3ClientSettingsResolver.resolve(props);
        return S3Client.builder()
                .region(Region.of(resolved.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(resolved.pathStyleAccess())
                        .chunkedEncodingEnabled(resolved.chunkedEncodingEnabled())
                        .build())
                .endpointOverride(resolved.endpointUri())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .build();
    }

    @Test
    @DisplayName("R10A: materializes S3 object to local temp file")
    void materializesS3ObjectToLocalFile(@TempDir Path tempDir) throws Exception {
        S3Client s3Client = createS3Client();
        StorageS3Properties props = createProperties();

        // Ensure bucket exists
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (Exception ignored) {
            // Bucket may already exist
        }

        // Upload a test object
        byte[] testData = "hello rustfs r10a smoke".getBytes();
        String objectKey = "smoke/r10a-test-" + System.currentTimeMillis() + ".txt";
        String expectedChecksum = computeSha256(testData);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(objectKey)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromBytes(testData));

        try {
            // Materialize via S3ObjectMaterializer
            S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);
            assertTrue(materializer.isEnabled(), "Materializer must be enabled");

            Optional<S3ObjectMaterializer.MaterializedObject> result =
                    materializer.materialize(BUCKET, objectKey, expectedChecksum);

            assertTrue(result.isPresent(), "Materialization must succeed");
            S3ObjectMaterializer.MaterializedObject obj = result.get();

            // Verify local file
            assertTrue(Files.exists(obj.localPath()), "Local file must exist");
            assertEquals(testData.length, obj.sizeBytes(), "Size must match");
            assertEquals(expectedChecksum, obj.checksum(), "Checksum must match");
            assertEquals("text/plain", obj.contentType(), "Content type must match");

            // Verify file content
            byte[] downloaded = Files.readAllBytes(obj.localPath());
            assertArrayEquals(testData, downloaded, "Downloaded content must match uploaded");

            // Cleanup local file
            Files.deleteIfExists(obj.localPath());

        } finally {
            // Cleanup S3 object
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET).key(objectKey).build());
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("R10A: returns empty for non-existent S3 object")
    void returnsEmptyForNonExistentObject() {
        StorageS3Properties props = createProperties();
        S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

        Optional<S3ObjectMaterializer.MaterializedObject> result =
                materializer.materialize(BUCKET, "nonexistent/key-" + System.currentTimeMillis(), null);

        assertTrue(result.isEmpty(), "Must return empty for non-existent object");
    }

    @Test
    @DisplayName("R10A: returns empty for checksum mismatch")
    void returnsEmptyForChecksumMismatch() throws Exception {
        S3Client s3Client = createS3Client();
        StorageS3Properties props = createProperties();

        // Ensure bucket exists
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (Exception ignored) {}

        // Upload a test object
        byte[] testData = "checksum mismatch test".getBytes();
        String objectKey = "smoke/r10a-checksum-" + System.currentTimeMillis() + ".txt";

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(objectKey)
                        .build(),
                RequestBody.fromBytes(testData));

        try {
            S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

            // Use wrong checksum
            Optional<S3ObjectMaterializer.MaterializedObject> result =
                    materializer.materialize(BUCKET, objectKey, "0000000000000000000000000000000000000000000000000000000000000000");

            assertTrue(result.isEmpty(), "Must return empty for checksum mismatch");
        } finally {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET).key(objectKey).build());
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("R10A: materializes without checksum verification when no expected checksum")
    void materializesWithoutChecksumVerification() throws Exception {
        S3Client s3Client = createS3Client();
        StorageS3Properties props = createProperties();

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (Exception ignored) {}

        byte[] testData = "no checksum verification".getBytes();
        String objectKey = "smoke/r10a-nocheck-" + System.currentTimeMillis() + ".txt";

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(objectKey)
                        .build(),
                RequestBody.fromBytes(testData));

        try {
            S3ObjectMaterializer materializer = new S3ObjectMaterializer(props);

            Optional<S3ObjectMaterializer.MaterializedObject> result =
                    materializer.materialize(BUCKET, objectKey, null);

            assertTrue(result.isPresent(), "Must succeed without expected checksum");
            assertEquals(testData.length, result.get().sizeBytes());

            Files.deleteIfExists(result.get().localPath());
        } finally {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET).key(objectKey).build());
            } catch (Exception ignored) {}
        }
    }

    private static String computeSha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
