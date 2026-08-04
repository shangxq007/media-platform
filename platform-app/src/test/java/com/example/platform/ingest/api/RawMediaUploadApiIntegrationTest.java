package com.example.platform.ingest.api;

import com.example.platform.render.app.input.RenderInputMaterialization;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.domain.BlobStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "storage.s3.enabled=false",
        "app.storage.local-root=${java.io.tmpdir}/media-platform-f1-upload-api-storage",
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
class RawMediaUploadApiIntegrationTest extends PostgresTestContainerSupport {

    private static final byte[] PAYLOAD = "f1-raw-media-upload-payload\nframe=0001\n".getBytes(StandardCharsets.UTF_8);
    private static final String FILENAME = "f1-upload-sample.mp4";
    private static final String CONTENT_TYPE = "video/mp4";

    @LocalServerPort
    private int port;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BlobStorage blobStorage;

    @Autowired
    private RenderInputMaterializationService materializationService;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void uploadRawMediaPersistsAssetProductAndBlobThroughHttpEndpoint() throws Exception {
        String unique = HexFormat.of().formatHex(Long.toString(System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        String tenantId = createTenant("f1-upload-tenant-" + unique);
        String projectId = createProject(tenantId, "f1-upload-project-" + unique);

        HttpResponse<String> response = postMultipartRawMedia(tenantId, projectId, PAYLOAD, FILENAME, CONTENT_TYPE,
                "F1 Upload Sample " + unique);

        Assertions.assertEquals(200, response.statusCode(), response.body());
        Assertions.assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        JsonNode body = mapper.readTree(response.body());
        Assertions.assertEquals("SUCCESS", body.get("status").asText());
        String productId = body.get("productId").asText();
        Assertions.assertFalse(productId.isBlank());
        Assertions.assertFalse(body.get("createdAt").asText().isBlank());
        Assertions.assertFalse(response.body().contains("storageReference"), "response must not expose storage internals");

        Product product = productRepository.findById(productId).orElseThrow();
        Assertions.assertEquals(tenantId, product.tenantId());
        Assertions.assertEquals(projectId, product.projectId());
        Assertions.assertEquals(ProductType.RAW_MEDIA, product.productType());
        Assertions.assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
        Assertions.assertEquals(ProductStatus.READY, product.status());
        Assertions.assertEquals("user-upload", product.producerType());
        Assertions.assertEquals(CONTENT_TYPE, product.mimeType());
        Assertions.assertNotNull(product.ownerAssetId());
        Assertions.assertNotNull(product.storageReferenceId());

        Asset asset = assetRepository.findById(tenantId, product.ownerAssetId()).orElseThrow();
        Assertions.assertEquals(tenantId, asset.tenantId());
        Assertions.assertEquals(projectId, asset.projectId());
        Assertions.assertEquals(FILENAME, asset.filename());
        Assertions.assertEquals("VIDEO", asset.mediaType());
        Assertions.assertEquals(PAYLOAD.length, asset.sizeBytes());
        Assertions.assertEquals("DRAFT", asset.publishStatus());
        Assertions.assertEquals(asset.id(), product.ownerAssetId());
        Assertions.assertTrue(asset.storageKey().contains(tenantId));
        Assertions.assertTrue(asset.storageKey().contains(projectId));
        Assertions.assertTrue(asset.storageKey().endsWith("/" + FILENAME));

        Optional<byte[]> storedBytes = blobStorage.get("uploads", asset.storageKey());
        Assertions.assertTrue(storedBytes.isPresent(), "uploaded bytes must be retrievable through BlobStorage");
        Assertions.assertArrayEquals(PAYLOAD, storedBytes.orElseThrow());

        RenderInputMaterialization materialization = materializationService.materialize(
                product.productId(), asset.id(), "clip-f2a-" + unique);
        Assertions.assertTrue(materialization.valid(), "uploaded RAW_MEDIA must materialize: "
                + materialization.failureReason());
        Assertions.assertEquals(product.productId(), materialization.inputProductId());
        Assertions.assertEquals(product.storageReferenceId(), materialization.storageReferenceId());
        Assertions.assertTrue(materialization.materializedPath().toString().contains(asset.storageKey()));
        Assertions.assertArrayEquals(PAYLOAD, java.nio.file.Files.readAllBytes(materialization.materializedPath()));

        List<Asset> assets = assetRepository.listByProject(tenantId, projectId);
        Assertions.assertEquals(1, assets.stream().filter(a -> FILENAME.equals(a.filename())).count());
        Assertions.assertEquals(1, productRepository.findByAsset(asset.id()).stream()
                .filter(p -> p.productType() == ProductType.RAW_MEDIA)
                .count());
    }

    private String createTenant(String name) throws Exception {
        HttpResponse<String> response = postJson("/api/v1/identity/tenants", "{\"name\":\"" + name + "\"}");
        Assertions.assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).get("id").asText();
    }

    private String createProject(String tenantId, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"F1 integration test\"}";
        HttpResponse<String> response = postJson("/api/v1/identity/tenants/" + tenantId + "/projects", body);
        Assertions.assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).get("id").asText();
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postMultipartRawMedia(String tenantId, String projectId, byte[] payload,
            String filename, String contentType, String displayName) throws Exception {
        String boundary = "----f1UploadBoundary" + System.nanoTime();
        byte[] body = multipartBody(boundary, payload, filename, contentType, displayName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/upload/raw-media"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] multipartBody(String boundary, byte[] fileBytes, String filename,
            String contentType, String displayName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextPart(out, boundary, "displayName", displayName);
        writeTextPart(out, boundary, "contentType", contentType);
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value)
            throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
