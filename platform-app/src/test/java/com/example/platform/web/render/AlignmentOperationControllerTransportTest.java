package com.example.platform.web.render;

import com.example.platform.operation.application.AlignmentOperationService;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L5-V2: the alignment HTTP transport DTO maps to the typed canonical command without Spring or
 * persistence (production mapping code only).
 */
class AlignmentOperationControllerTransportTest {

    private static AlignmentOperationController.CreateAlignmentRequest request(
            List<String> source, List<String> derived, List<AlignmentOperationController.AttributeDto> attrs) {
        return new AlignmentOperationController.CreateAlignmentRequest(
                "R100", "content-hash", "align-1", source, derived, attrs);
    }

    @Test
    void mapsToTypedCommand() {
        var attrs = List.of(
                new AlignmentOperationController.AttributeDto("priority", "NUMBER", "3"),
                new AlignmentOperationController.AttributeDto("lang", "TEXT", "zh-CN"));

        AlignmentOperationService.CreateAlignmentCommand command =
                request(List.of("s2", "s1"), List.of("d1"), attrs).toCommand();

        assertEquals("align-1", command.alignmentId().value());
        assertEquals(List.of("s1", "s2"),
                command.sourceUnits().stream().map(u -> u.value()).toList(), "endpoints normalized");
        assertEquals(List.of("d1"), command.derivedUnits().stream().map(u -> u.value()).toList());
        assertEquals(2, command.attributes().size());
        assertInstanceOf(AlignmentAttributeValue.NumberValue.class, command.attributes().get(0).value());
        assertInstanceOf(AlignmentAttributeValue.TextValue.class, command.attributes().get(1).value());
        assertEquals("R100", command.baseRevisionId());
    }

    @Test
    void rejectsEmptyEndpointSets() {
        assertThrows(AlignmentOperationController.AlignmentTransportException.class,
                () -> request(List.of(), List.of("d1"), List.of()));
        assertThrows(AlignmentOperationController.AlignmentTransportException.class,
                () -> request(List.of("s1"), List.of(), List.of()));
    }

    @Test
    void rejectsUnknownAttributeKind() {
        var bad = new AlignmentOperationController.AttributeDto("tag", "WEIRD", "x");
        assertThrows(AlignmentOperationController.AlignmentTransportException.class,
                () -> request(List.of("s1"), List.of("d1"), List.of(bad)).toCommand());
    }

    @Test
    void acceptsBase64ContentHash() {
        var req = new AlignmentOperationController.CreateAlignmentRequest(
                "R100", "f1NKItKjrJ2xs6N/cbD68+OpM3Ib1qr9kzG+U1VdDNM=",
                "align-1", List.of("s1"), List.of("d1"), List.of());
        assertTrue(req.toCommand().baseContentHash().contains("="));
    }
}
