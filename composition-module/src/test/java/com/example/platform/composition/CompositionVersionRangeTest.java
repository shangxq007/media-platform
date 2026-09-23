package com.example.platform.composition;

import com.example.platform.composition.domain.CompositionVersionRange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositionVersionRangeTest {
    @TestFactory List<DynamicTest> canonicalRangeConformance() throws Exception {
        var cases = new ObjectMapper().readTree(getClass().getResourceAsStream("/composition/version-range-cases.json"));
        List<DynamicTest> tests = new ArrayList<>();
        cases.forEach(c -> tests.add(DynamicTest.dynamicTest(c.toString(), () ->
            assertEquals(c.get("expected").asText(), CompositionVersionRange.check(c.get("range").asText(), c.get("actual").asText())))));
        return tests;
    }
}
