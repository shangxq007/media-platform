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

    private static final String BASE = "/api/tenants/tenant-a/workflow-definitions";
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

    @Test
    void createUpdateValidatePublishReadRoundtrip() throws Exception {
        assertContainedWithoutWorkflowWrite("POST", BASE, validBody("wf"));
    }

    @Test
    void tenantIsolationReturns404WithoutExistenceLeak() throws Exception {
        assertContainedWithoutWorkflowWrite(
                "GET", "/api/tenants/tenant-b/workflow-definitions/request-definition", null);
    }

    @Test
    void unknownVersionReturns404() throws Exception {
        assertContainedWithoutWorkflowWrite(
                "GET", BASE + "/request-definition/versions/99", null);
    }

    @Test
    void invalidGraphRejectedWith422() throws Exception {
        String selfEdgeBody = createBody("wf", "[" + VALID_NODE + "]", edgeBody("e1", "n0", "n0"));
        assertContainedWithoutWorkflowWrite("POST", BASE, selfEdgeBody);
    }

    @Test
    void oversizedGraphRejectedWith422() throws Exception {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) nodes.append(",");
            nodes.append(nodeJson("n" + i, "ACTION", "{\"capabilityKey\":\"k" + i + "\",\"capabilityVersion\":\"1\"}"));
        }
        assertContainedWithoutWorkflowWrite(
                "POST", BASE, createBody("wf-big", "[" + nodes + "]", "[]"));
    }

    @Test
    void publishedMutationRejectedWith409() throws Exception {
        assertContainedWithoutWorkflowWrite("PUT", BASE + "/request-definition/versions/1",
                "{\"name\":\"mutated\",\"description\":null,\"nodes\":[" + VALID_NODE + "],\"edges\":[],"
                        + "\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}"
                        + ",\"optimisticVersion\":3}");
    }

    @Test
    void optimisticConflictRejectedWith409() throws Exception {
        assertContainedWithoutWorkflowWrite("PUT", BASE + "/request-definition/versions/1",
                "{\"name\":\"stale\",\"description\":null,\"nodes\":[" + VALID_NODE + "],\"edges\":[],"
                        + "\"parameters\":[],\"trigger\":{\"triggerType\":\"MANUAL\",\"referenceId\":null,\"referenceVersion\":null}"
                        + ",\"optimisticVersion\":42}");
    }

    @Test
    void unknownNodeTypeRejectedWith400() throws Exception {
        String badNode = nodeJson("n0", "BOGUS", "{}");
        assertContainedWithoutWorkflowWrite(
                "POST", BASE, createBody("wf", "[" + badNode + "]", "[]"));
    }

    @Test
    void secretLikeConfigRejectedWith400() throws Exception {
        String secretNode = nodeJson("n0", "ACTION",
                "{\"capabilityKey\":\"ghp_secretvalue12345\",\"capabilityVersion\":\"1\"}");
        assertContainedWithoutWorkflowWrite(
                "POST", BASE, createBody("wf", "[" + secretNode + "]", "[]"));
    }

    @Test
    void publishFromDraftRejectedWith409() throws Exception {
        assertContainedWithoutWorkflowWrite(
                "POST", BASE + "/request-definition/versions/1/publish", "{\"optimisticVersion\":1}");
    }

    private void assertContainedWithoutWorkflowWrite(String method, String path, String body) throws Exception {
        int definitionsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_workflow_definition", Integer.class);
        int versionsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_workflow_definition_version", Integer.class);
        HttpResponse<String> response = send(method, path, body);
        assertEquals(403, response.statusCode(), response.body());
        assertEquals(definitionsBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_workflow_definition", Integer.class),
                "Denied request must not dispatch a workflow-definition write");
        assertEquals(versionsBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_workflow_definition_version", Integer.class),
                "Denied request must not dispatch a workflow-version write");
    }
}
