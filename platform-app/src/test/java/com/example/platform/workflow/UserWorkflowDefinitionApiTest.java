package com.example.platform.workflow;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UWD-RED-016 (API). Real TCP HTTP contract tests (RealHttpSecurityBoundaryTest
 * convention: @SpringBootTest RANDOM_PORT + Java HttpClient + Testcontainers).
 * Authentic RED: compiles, then fails at runtime because the W2 routes do not
 * exist yet (HTTP 404 on every request).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "spring.mvc.throw-exception-if-no-handler-found=true",
    "spring.web.resources.add-mappings=false"
})
class UserWorkflowDefinitionApiTest extends PostgresTestContainerSupport {

    private static final String BASE = "/api/v1/tenants/tenant-a/workflow-definitions";
    private static final Pattern ID_PATTERN = Pattern.compile("\"definitionId\":\"([^\"]+)\"");
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"versionNumber\":([0-9]+)");
    private static final Pattern OPT_VERSION_PATTERN = Pattern.compile("\"optimisticVersion\":([0-9]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)));
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String nodeJson(String nodeId, String nodeType, String config) {
        return "{\"nodeId\":\"" + nodeId + "\",\"nodeType\":\"" + nodeType + "\",\"name\":\"node-" + nodeId
                + "\",\"configSchemaRef\":\"w2/action/config/v1\",\"configValues\":" + config
                + ",\"inputDeclarations\":[],\"outputDeclarations\":[],\"errorPolicy\":\"FAIL\"}";
    }

    private static String createBody(String name, String nodes, String edges) {
        return "{\"name\":\"" + name + "\",\"description\":null,\"projectId\":null,\"schemaVersion\":1"
                + ",\"nodes\":" + nodes + ",\"edges\":" + edges
                + ",\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}}";
    }

    private static final String VALID_NODE = nodeJson("n0", "ACTION",
            "{\"capabilityKey\":\"render.render-job.create\",\"capabilityVersion\":\"1\"}");

    private static String validBody(String name) {
        return createBody(name, "[" + VALID_NODE + "]", "[]");
    }

    private static String edgeBody(String edgeId, String from, String to) {
        return "[{\"edgeId\":\"" + edgeId + "\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"conditionRef\":\"\",\"sortOrder\":0}]";
    }

    private String createDefinition(String tenant, String name, String body) throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/tenants/" + tenant + "/workflow-definitions", body);
        assertEquals(201, r.statusCode(), r.body());
        Matcher m = ID_PATTERN.matcher(r.body());
        assertTrue(m.find(), r.body());
        return m.group(1);
    }

    @Test
    void createUpdateValidatePublishReadRoundtrip() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));

        // get latest
        HttpResponse<String> latest = send("GET", BASE + "/" + id, null);
        assertEquals(200, latest.statusCode());

        // update draft
        HttpResponse<String> updated = send("PUT", BASE + "/" + id + "/versions/1",
                "{\"name\":\"wf2\",\"description\":null,\"nodes\":[" + VALID_NODE + "],\"edges\":[],"
                        + "\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}"
                        + ",\"optimisticVersion\":1}");
        assertEquals(200, updated.statusCode(), updated.body());

        // validate
        HttpResponse<String> validated = send("POST", BASE + "/" + id + "/versions/1/validate",
                "{\"optimisticVersion\":2}");
        assertEquals(200, validated.statusCode(), validated.body());

        // publish
        HttpResponse<String> published = send("POST", BASE + "/" + id + "/versions/1/publish",
                "{\"optimisticVersion\":3}");
        assertEquals(200, published.statusCode(), published.body());
        assertTrue(published.body().contains("\"status\":\"PUBLISHED\""));

        // new version
        HttpResponse<String> v2 = send("POST", BASE + "/" + id + "/versions",
                "{\"sourceVersion\":1}");
        assertEquals(201, v2.statusCode(), v2.body());
        assertTrue(v2.body().contains("\"versionNumber\":2"));

        // get exact version
        HttpResponse<String> exact = send("GET", BASE + "/" + id + "/versions/1", null);
        assertEquals(200, exact.statusCode());

        // archive
        HttpResponse<String> archived = send("POST", BASE + "/" + id + "/versions/1/archive",
                "{\"optimisticVersion\":4}");
        assertEquals(200, archived.statusCode(), archived.body());
        assertTrue(archived.body().contains("\"status\":\"ARCHIVED\""));

        // list
        HttpResponse<String> list = send("GET", BASE, null);
        assertEquals(200, list.statusCode());
    }

    @Test
    void tenantIsolationReturns404WithoutExistenceLeak() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));
        HttpResponse<String> r = send("GET", "/api/v1/tenants/tenant-b/workflow-definitions/" + id, null);
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("WORKFLOW-404-001"), r.body());
    }

    @Test
    void unknownVersionReturns404() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));
        HttpResponse<String> r = send("GET", BASE + "/" + id + "/versions/99", null);
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("WORKFLOW-404-002"), r.body());
    }

    @Test
    void invalidGraphRejectedWith422() throws Exception {
        String selfEdgeBody = createBody("wf", "[" + VALID_NODE + "]", edgeBody("e1", "n0", "n0"));
        HttpResponse<String> r = send("POST", BASE, selfEdgeBody);
        assertEquals(422, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-422-001"), r.body());
    }

    @Test
    void oversizedGraphRejectedWith422() throws Exception {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) nodes.append(",");
            nodes.append(nodeJson("n" + i, "ACTION", "{\"capabilityKey\":\"k" + i + "\",\"capabilityVersion\":\"1\"}"));
        }
        HttpResponse<String> r = send("POST", BASE, createBody("wf-big", "[" + nodes + "]", "[]"));
        assertEquals(422, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-422-001"), r.body());
    }

    @Test
    void publishedMutationRejectedWith409() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));
        send("POST", BASE + "/" + id + "/versions/1/validate", "{\"optimisticVersion\":1}");
        HttpResponse<String> published = send("POST", BASE + "/" + id + "/versions/1/publish", "{\"optimisticVersion\":2}");
        assertEquals(200, published.statusCode());

        HttpResponse<String> mutation = send("PUT", BASE + "/" + id + "/versions/1",
                "{\"name\":\"mutated\",\"description\":null,\"nodes\":[" + VALID_NODE + "],\"edges\":[],"
                        + "\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}"
                        + ",\"optimisticVersion\":3}");
        assertEquals(409, mutation.statusCode(), mutation.body());
        assertTrue(mutation.body().contains("WORKFLOW-409-002"), mutation.body());
    }

    @Test
    void optimisticConflictRejectedWith409() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));
        HttpResponse<String> r = send("PUT", BASE + "/" + id + "/versions/1",
                "{\"name\":\"stale\",\"description\":null,\"nodes\":[" + VALID_NODE + "],\"edges\":[],"
                        + "\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}"
                        + ",\"optimisticVersion\":42}");
        assertEquals(409, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-409-003"), r.body());
    }

    @Test
    void unknownNodeTypeRejectedWith400() throws Exception {
        String badNode = nodeJson("n0", "BOGUS", "{}");
        HttpResponse<String> r = send("POST", BASE, createBody("wf", "[" + badNode + "]", "[]"));
        assertEquals(400, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-400-009"), r.body());
    }

    @Test
    void secretLikeConfigRejectedWith400() throws Exception {
        String secretNode = nodeJson("n0", "ACTION",
                "{\"capabilityKey\":\"ghp_secretvalue12345\",\"capabilityVersion\":\"1\"}");
        HttpResponse<String> r = send("POST", BASE, createBody("wf", "[" + secretNode + "]", "[]"));
        assertEquals(400, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-400-010"), r.body());
    }

    @Test
    void publishFromDraftRejectedWith409() throws Exception {
        String id = createDefinition("tenant-a", "wf", validBody("wf"));
        HttpResponse<String> r = send("POST", BASE + "/" + id + "/versions/1/publish", "{\"optimisticVersion\":1}");
        assertEquals(409, r.statusCode(), r.body());
        assertTrue(r.body().contains("WORKFLOW-409-001"), r.body());
    }
}
