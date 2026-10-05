package com.example.platform.operation.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Operation V2: resolver finds V2 definitions AND keeps every V1 resolution unchanged. */
class OperationV2ResolverTest {

    @Test
    void findDefinitionResolvesCreateAlignment() throws Exception {
        OperationDefinition def = OperationRequestResolver.findDefinition(
                OperationDefinitionId.of("timeline.alignment.create"));
        assertEquals(OperationDefinition.V2.CREATE_ALIGNMENT, def, "AC-OPS-6");
    }

    @Test
    void findDefinitionStillResolvesEveryV1Operation() throws Exception {
        for (OperationDefinition v1 : OperationDefinition.V1.ALL) {
            assertEquals(v1, OperationRequestResolver.findDefinition(v1.definitionId()),
                    "V1 resolution unchanged for " + v1.definitionId().value());
        }
    }

    @Test
    void unknownDefinitionFailsClosed() {
        assertThrows(OperationResolutionException.class,
                () -> OperationRequestResolver.findDefinition(OperationDefinitionId.of("timeline.alignment.nope")));
    }
}
