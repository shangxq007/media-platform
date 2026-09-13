package com.example.platform.ingest.api;

import com.example.platform.render.app.input.RenderInputMaterialization;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.media.api.Asset;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.media.api.MediaAssets;
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
        "app.security.enabled=true", "app.security.oauth2.enabled=false", "app.outbox.dispatcher-enabled=false",
        "app.security.jwt.secret-key=ep10-upload-isolated-jwt-test-key-at-least-256-bits",
        "app.identity.api-key-auth-enabled=false",
        "storage.s3.enabled=false",
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
class RawMediaUploadApiIntegrationTest extends PostgresTestContainerSupport {

    private static final java.nio.file.Path STORAGE_ROOT = createRoot();
    private static java.nio.file.Path createRoot() {
        try {return java.nio.file.Files.createTempDirectory("ep10-upload-");}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    @org.springframework.test.context.DynamicPropertySource
    static void storage(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-root",STORAGE_ROOT::toString);
    }
    @org.junit.jupiter.api.AfterAll static void cleanupOutput() throws Exception {
        try(var paths=java.nio.file.Files.walk(STORAGE_ROOT)) {
            for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);
        }
    }
    private static final byte[] PAYLOAD = "f1-raw-media-upload-payload\nframe=0001\n".getBytes(StandardCharsets.UTF_8);
    private static final String FILENAME = "f1-upload-sample.mp4";
    private static final String CONTENT_TYPE = "video/mp4";

    @LocalServerPort
    private int port;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private MediaAssets assetRepository;

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

    @org.junit.jupiter.api.AfterEach void clearTenant() {com.example.platform.shared.web.TenantContext.clear();}

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

        com.example.platform.shared.web.TenantContext.set(tenantId);
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

        var listRequest=HttpRequest.newBuilder(URI.create(baseUrl+"/api/projects/"+projectId+"/assets"))
                .header("Authorization","Bearer "+token(tenantId,"uploader")).GET().build();
        var listResponse=client.send(listRequest,HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200,listResponse.statusCode(),listResponse.body());
        Assertions.assertEquals(1,mapper.readTree(listResponse.body()).size());
        Assertions.assertEquals(1, productRepository.findByAsset(asset.id()).stream()
                .filter(p -> p.productType() == ProductType.RAW_MEDIA)
                .count());
    }

    private String createTenant(String name) {
        String id="ten_"+java.util.UUID.randomUUID().toString().replace("-","");
        jdbc.update("insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",id,name);return id;
    }
    private String createProject(String tenantId,String name) {
        String id="prj_"+java.util.UUID.randomUUID().toString().replace("-","");
        jdbc.update("insert into project(id,tenant_id,name,status,created_at) values (?,?,?,'ACTIVE',now())",id,tenantId,name);
        String role="upload-"+java.util.UUID.randomUUID();
        jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",role,role,"Upload fixture");
        for(String key:List.of("READ","WRITE")) {
            String permission="perm-"+java.util.UUID.randomUUID();
            jdbc.update("insert into permission(id,permission_key,name,created_at) values (?,?,?,now()) on conflict(permission_key) do nothing",permission,key,key);
            String actual=jdbc.queryForObject("select id from permission where permission_key=?",String.class,key);
            jdbc.update("insert into role_permission(id,role_id,permission_id,created_at) values (?,?,?,now())","rp-"+java.util.UUID.randomUUID(),role,actual);
        }
        jdbc.update("insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,'uploader',?,now())","ra-"+java.util.UUID.randomUUID(),tenantId,id,role);
        return id;
    }
    private String token(String tenant,String user) {
        return io.jsonwebtoken.Jwts.builder().subject(user).claim("tenantId",tenant).claim("roles",List.of("USER"))
                .expiration(new java.util.Date(System.currentTimeMillis()+600000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("ep10-upload-isolated-jwt-test-key-at-least-256-bits".getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void deniedUploadCannotWriteRowsOrPhysicalObjects() throws Exception {
        String tenant=createTenant("Denied upload"),project=createProject(tenant,"Denied project");
        int objectsBefore=blobStorage.listObjects("uploads","",1000).size();
        String boundary="ep10-denied-boundary";
        var request=HttpRequest.newBuilder(URI.create(baseUrl+"/api/tenants/"+tenant+"/projects/"+project+"/upload/raw-media"))
                .header("Authorization","Bearer "+token(tenant,"denied"))
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary,PAYLOAD,FILENAME,CONTENT_TYPE,"Denied"))).build();
        var result=client.send(request,HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403,result.statusCode(),result.body());
        Assertions.assertEquals(0,jdbc.queryForObject("select count(*) from media_asset where tenant_id=?",Integer.class,tenant));
        Assertions.assertEquals(0,jdbc.queryForObject("select count(*) from product where tenant_id=?",Integer.class,tenant));
        Assertions.assertEquals(objectsBefore,blobStorage.listObjects("uploads","",1000).size());
    }

    private HttpResponse<String> postMultipartRawMedia(String tenantId, String projectId, byte[] payload,
            String filename, String contentType, String displayName) throws Exception {
        String boundary = "----f1UploadBoundary" + System.nanoTime();
        byte[] body = multipartBody(boundary, payload, filename, contentType, displayName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tenants/" + tenantId + "/projects/" + projectId
                        + "/upload/raw-media"))
                .header("Authorization", "Bearer " + token(tenantId,"uploader"))
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
