package com.example.platform.render.domain.timeline.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineConflictExceptionTest {

    @Test
    void errorCode_isCorrect() {
        assertEquals("TIMELINE_REVISION_CONFLICT", TimelineConflictException.ERROR_CODE);
    }

    @Test
    void message_containsRelevantInfo() {
        var ex = new TimelineConflictException("prod-1", "rev-1", "rev-2");

        assertTrue(ex.getMessage().contains("prod-1"));
        assertTrue(ex.getMessage().contains("rev-1"));
        assertTrue(ex.getMessage().contains("rev-2"));
    }

    @Test
    void getters_returnCorrectValues() {
        var ex = new TimelineConflictException("prod-1", "rev-expected", "rev-actual");

        assertEquals("prod-1", ex.getProductId());
        assertEquals("rev-expected", ex.getExpectedRevisionId());
        assertEquals("rev-actual", ex.getActualRevisionId());
    }

    @Test
    void isRuntimeException() {
        var ex = new TimelineConflictException("prod-1", "rev-1", "rev-2");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
