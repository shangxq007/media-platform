package com.example.platform.web.render;

import com.example.platform.operation.application.TimelineBatchCommandMapper;
import com.example.platform.operation.operation.OperationDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticWireContractTest {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        write(directory.resolve("timeline-batch-v2.schema.json"), TimelineBatchSchema.batch());
        write(directory.resolve("timeline-composition-aspect-v2.schema.json"), TimelineBatchSchema.step(OperationDefinition.V3.SET_COMPOSITION_ASPECT));
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
    }

    @Test void schemaExportsMatchProduction() throws Exception {
        Path directory = Path.of("../contracts/http/media-api");
        assertEquals(JSON.valueToTree(TimelineBatchSchema.batch()), JSON.readTree(directory.resolve("timeline-batch-v2.schema.json").toFile()));
        assertEquals(JSON.valueToTree(TimelineBatchSchema.step(OperationDefinition.V3.SET_COMPOSITION_ASPECT)),
                JSON.readTree(directory.resolve("timeline-composition-aspect-v2.schema.json").toFile()));
    }

    @Test void openApi31PublishesTheCompleteAdmissionSchema() throws Exception {
        var operation = new io.swagger.v3.oas.models.Operation().requestBody(
                new io.swagger.v3.oas.models.parameters.RequestBody().content(
                        new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                                new io.swagger.v3.oas.models.media.MediaType())));
        var api = new io.swagger.v3.oas.models.OpenAPI().path("/project/timeline-operations/batch/v2/preview",
                new io.swagger.v3.oas.models.PathItem().post(operation));
        new SemanticOpenApiConfiguration().semanticBatchRequestSchema().customise(api);
        var actual = operation.getRequestBody().getContent().get("application/json").getSchema();
        var serialized = io.swagger.v3.core.util.Json31.mapper().writeValueAsString(actual);
        assertEquals(JSON.valueToTree(TimelineBatchSchema.batch()), JSON.readTree(serialized));
    }

    @Test void actualDecoderRejectsInvalidWireVariantsBeforePlanning() throws Exception {
        var batch = Map.of("batchId", "sample", "baseRevisionId", "r1", "baseContentHash", "A".repeat(43) + "=");
        var target = Map.of("textElementId", "title");
        var step = Map.of("definitionId", "timeline.text.remove", "target", target);
        var decoder = new TimelineBatchCommandMapper();
        java.util.function.Consumer<Map<?, ?>> parse = op -> {
            var request = new java.util.HashMap<String,Object>(batch);
            request.put("operations", List.of(op));
            try {
                decoder.toBatchRequest("tenant", "project", TimelineBatchOperationController.decode(JSON.writeValueAsString(request)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        };
        parse.accept(step);
        assertThrows(RuntimeException.class, () -> parse.accept(Map.of("definitionId", "timeline.text.remove", "target", Map.of("clipId", "clip"))));
        assertThrows(RuntimeException.class, () -> parse.accept(Map.of("definitionId", "timeline.text.remove", "target", target, "version", "9.1")));
        assertThrows(RuntimeException.class, () -> parse.accept(Map.of("definitionId", "timeline.text.remove", "target", target, "parameters", Map.of("textElementId", "duplicate"))));
        assertThrows(RuntimeException.class, () -> parse.accept(Map.of("definitionId", "timeline.trim", "target", Map.of("clipId", "clip"), "parameters", Map.of("edge", "END"))));
    }
}
