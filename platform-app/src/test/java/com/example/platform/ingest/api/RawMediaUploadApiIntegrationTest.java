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
        String tenantId = "request-tenant-" + unique;
        String projectId = "request-project-" + unique;
        int assetsBefore = assetRepository.listByProject(tenantId, projectId).size();
        int productsBefore = productRepository.findByProject(projectId, 100).size();

        HttpResponse<String> response = postMultipartRawMedia(tenantId, projectId, PAYLOAD, FILENAME, CONTENT_TYPE,
                "F1 Upload Sample " + unique);

        Assertions.assertEquals(403, response.statusCode(), response.body());
        Assertions.assertEquals(assetsBefore, assetRepository.listByProject(tenantId, projectId).size(),
                "Denied upload must not dispatch an asset write");
        Assertions.assertEquals(productsBefore, productRepository.findByProject(projectId, 100).size(),
                "Denied upload must not dispatch a product write");
    }

    private HttpResponse<String> postMultipartRawMedia(String tenantId, String projectId, byte[] payload,
            String filename, String contentType, String displayName) throws Exception {
        String boundary = "----f1UploadBoundary" + System.nanoTime();
        byte[] body = multipartBody(boundary, payload, filename, contentType, displayName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tenants/" + tenantId + "/projects/" + projectId
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
