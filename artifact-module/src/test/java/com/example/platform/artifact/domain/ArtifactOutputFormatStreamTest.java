package com.example.platform.artifact.domain;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactOutputFormatStreamTest {
    @Test void streamedMp4AcceptsValidBrandBeyondSmallHeaderBuffer() throws Exception {
        byte[] bytes = new byte[70_000];
        ByteBuffer.wrap(bytes).putInt(bytes.length);
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        System.arraycopy("isom".getBytes(StandardCharsets.US_ASCII), 0, bytes, bytes.length - 4, 4);
        ArtifactOutputFormat.MP4.validate(bytes);
        ArtifactOutputFormat.MP4.validate(new ByteArrayInputStream(bytes), bytes.length);
        assertThrows(IllegalArgumentException.class, () ->
                ArtifactOutputFormat.QUICKTIME.validate(new ByteArrayInputStream(bytes), bytes.length));
    }
}
