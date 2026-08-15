package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEMPORAL_MAPPING_POST_CLOSE (assertion C): TemporalMapping changes are
 * classified with typed semantic detail — direction/kind/freeze-position
 * changes are NEVER collapsed into a pure speed change.
 */
class TemporalMappingDiffClassificationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode tm(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static List<SemanticChangeType> classify(String oldJson, String newJson) throws Exception {
        List<SemanticChange> out = new ArrayList<>();
        TimelineSemanticDiffService.classifyTemporalMappingChange(
                tm(oldJson), tm(newJson), new EntityRef(EntityKind.CLIP, "c1"), out);        return out.stream().map(SemanticChange::type).toList();
    }

    @Test
    void pureRateChangeClassifiesAsSpeed() throws Exception {
        var types = classify(
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"FORWARD\"}",
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"4/1\",\"direction\":\"FORWARD\"}");
        assertTrue(types.contains(SemanticChangeType.CLIP_SPEED_CHANGED), "pure rate change -> speed");
        assertFalse(types.contains(SemanticChangeType.TEMPORAL_MAPPING_DIRECTION_CHANGED));
        assertFalse(types.contains(SemanticChangeType.TEMPORAL_MAPPING_KIND_CHANGED));
    }

    @Test
    void directionChangeIsNotSpeedOnly() throws Exception {
        var types = classify(
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"FORWARD\"}",
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"REVERSE\"}");
        assertTrue(types.contains(SemanticChangeType.TEMPORAL_MAPPING_DIRECTION_CHANGED),
                "direction change must not claim only speed changed");
        assertFalse(types.contains(SemanticChangeType.CLIP_SPEED_CHANGED),
                "same rate magnitude -> no pure speed change");
    }

    @Test
    void kindChangeClassifiesAsKindChange() throws Exception {
        var types = classify(
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"FORWARD\"}",
                "{\"kind\":\"FREEZE\",\"sourcePosition\":\"2/1\"}");
        assertTrue(types.contains(SemanticChangeType.TEMPORAL_MAPPING_KIND_CHANGED));
        assertFalse(types.contains(SemanticChangeType.CLIP_SPEED_CHANGED));
    }

    @Test
    void freezePositionChangeClassifiesAsFreezeChange() throws Exception {
        var types = classify(
                "{\"kind\":\"FREEZE\",\"sourcePosition\":\"1/1\"}",
                "{\"kind\":\"FREEZE\",\"sourcePosition\":\"3/1\"}");
        assertTrue(types.contains(SemanticChangeType.FREEZE_POSITION_CHANGED));
        assertFalse(types.contains(SemanticChangeType.CLIP_SPEED_CHANGED));
        assertFalse(types.contains(SemanticChangeType.TEMPORAL_MAPPING_KIND_CHANGED));
    }

    @Test
    void identicalMappingNoTemporalChange() throws Exception {
        var types = classify(
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"FORWARD\"}",
                "{\"kind\":\"CONSTANT_RATE\",\"rate\":\"2/1\",\"direction\":\"FORWARD\"}");
        assertFalse(types.contains(SemanticChangeType.CLIP_SPEED_CHANGED));
        assertFalse(types.contains(SemanticChangeType.TEMPORAL_MAPPING_KIND_CHANGED));
        assertFalse(types.contains(SemanticChangeType.TEMPORAL_MAPPING_DIRECTION_CHANGED));
    }
}
