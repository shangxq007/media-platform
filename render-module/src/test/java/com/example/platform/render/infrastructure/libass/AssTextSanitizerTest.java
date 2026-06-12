package com.example.platform.render.infrastructure.libass;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AssTextSanitizerTest {

    @Test
    void plainTextUnchanged() {
        assertEquals("Hello World", AssTextSanitizer.sanitize("Hello World"));
    }

    @Test
    void unicodePreserved() {
        assertEquals("你好世界", AssTextSanitizer.sanitize("你好世界"));
        assertEquals("مرحبا", AssTextSanitizer.sanitize("مرحبا"));
        assertEquals("🎉 Party!", AssTextSanitizer.sanitize("🎉 Party!"));
    }

    @Test
    void bracesEscaped() {
        String result = AssTextSanitizer.sanitize("text {with} braces");
        assertFalse(result.contains("{"), "Open brace should be removed");
        assertFalse(result.contains("}"), "Close brace should be removed");
        assertTrue(result.contains("text"));
        assertTrue(result.contains("with"));
        assertTrue(result.contains("braces"));
    }

    @Test
    void overrideTagsNeutralized() {
        String malicious = "{\\pos(0,0)}Hello";
        String result = AssTextSanitizer.sanitize(malicious);
        assertFalse(result.contains("{\\pos"), "Override tag should be neutralized");
        assertTrue(result.contains("Hello"), "Text content should be preserved");
    }

    @Test
    void fontOverrideNeutralized() {
        String malicious = "{\\fnMaliciousFont}text";
        String result = AssTextSanitizer.sanitize(malicious);
        assertFalse(result.contains("{\\fn"), "Font override should be neutralized");
    }

    @Test
    void clipOverrideNeutralized() {
        String malicious = "{\\clip(0,0,100,100)}text";
        String result = AssTextSanitizer.sanitize(malicious);
        assertFalse(result.contains("{\\clip"), "Clip override should be neutralized");
    }

    @Test
    void drawingModeOverrideNeutralized() {
        String malicious = "{\\p1}m 0 0 l 100 100{\\p0}";
        String result = AssTextSanitizer.sanitize(malicious);
        assertFalse(result.contains("{\\p1}"), "Drawing mode override should be neutralized");
        assertFalse(result.contains("{\\p0}"), "Drawing mode override should be neutralized");
    }

    @Test
    void newlinePreserved() {
        String result = AssTextSanitizer.sanitize("line1\nline2");
        // \n becomes \N after sanitization (ASS line break)
        assertTrue(result.contains("\\N"), "Newlines should be preserved as ASS line breaks: " + result);
    }

    @Test
    void carriageReturnNormalized() {
        String result = AssTextSanitizer.sanitize("line1\r\nline2\rline3");
        // Should not contain raw \r
        assertFalse(result.contains("\r"), "Carriage returns should be normalized");
    }

    @Test
    void commaSafe() {
        String result = AssTextSanitizer.sanitize("Hello, World!");
        assertTrue(result.contains("Hello"), "Text should be preserved");
        // Commas in dialogue text are fine — ASS uses positional fields
    }

    @Test
    void nullReturnsEmpty() {
        assertEquals("", AssTextSanitizer.sanitize(null));
    }

    @Test
    void blankReturnsEmpty() {
        assertEquals("", AssTextSanitizer.sanitize("   "));
    }

    @Test
    void longTextTruncated() {
        String longText = "A".repeat(20000);
        String result = AssTextSanitizer.sanitize(longText);
        assertTrue(result.length() <= 10000, "Text should be truncated to MAX_TEXT_LENGTH");
    }

    @Test
    void multipleOverrideTagsNeutralized() {
        String malicious = "{\\pos(0,0)}{\\alpha&H00&}{\\fnArial}Hello{\\b1}World{\\b0}";
        String result = AssTextSanitizer.sanitize(malicious);
        assertFalse(result.contains("{\\pos"));
        assertFalse(result.contains("{\\alpha"));
        assertFalse(result.contains("{\\fn"));
        assertFalse(result.contains("{\\b1"));
        assertFalse(result.contains("{\\b0"));
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
    }

    @Test
    void validStyleNameAccepted() {
        assertTrue(AssTextSanitizer.isValidStyleName("Default"));
        assertTrue(AssTextSanitizer.isValidStyleName("My Style"));
        assertTrue(AssTextSanitizer.isValidStyleName("style-1"));
    }

    @Test
    void invalidStyleNameRejected() {
        assertFalse(AssTextSanitizer.isValidStyleName(null));
        assertFalse(AssTextSanitizer.isValidStyleName(""));
        assertFalse(AssTextSanitizer.isValidStyleName("style{override}"));
        assertFalse(AssTextSanitizer.isValidStyleName("style\\tag"));
    }
}
