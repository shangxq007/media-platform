package com.example.platform.shared.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChecksummingInputStreamTest {

    @Test
    void shouldCountBytesAndComputeSha256() throws Exception {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        try (var cis = new ChecksummingInputStream(new ByteArrayInputStream(data))) {
            // Read all bytes
            while (cis.read() != -1) { /* drain */ }
            assertEquals(5, cis.sizeBytes());
            assertEquals("sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    cis.checksum());
        }
    }

    @Test
    void shouldWorkWithBufferedRead() throws Exception {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        try (var cis = new ChecksummingInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = new byte[4];
            int total = 0;
            int n;
            while ((n = cis.read(buf, 0, buf.length)) != -1) {
                total += n;
            }
            assertEquals(11, cis.sizeBytes());
            assertEquals(11, total);
            assertTrue(cis.checksum().startsWith("sha256:"));
        }
    }

    @Test
    void checksumShouldBeIdempotent() throws Exception {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        var cis = new ChecksummingInputStream(new ByteArrayInputStream(data));
        while (cis.read() != -1) { /* drain */ }
        String first = cis.checksum();
        String second = cis.checksum();
        assertEquals(first, second);
    }
}
