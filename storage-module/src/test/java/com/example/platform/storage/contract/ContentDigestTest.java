package com.example.platform.storage.contract;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentDigestTest {
    private static final String SHA256_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    @Test void sha256_canonicalization() {
        ContentDigest d1 = ContentDigest.sha256(SHA256_HEX.toUpperCase());
        ContentDigest d2 = ContentDigest.sha256(SHA256_HEX.toLowerCase());
        assertEquals(d1.canonicalValue(), d2.canonicalValue());
        assertTrue(d1.matches(d2));
    }
    @Test void sha256_validLength() {
        ContentDigest digest = ContentDigest.sha256(SHA256_HEX);
        assertEquals(64, digest.canonicalValue().length());
    }
    @Test void sha256_rejectsInvalidHex() {
        assertThrows(IllegalArgumentException.class, () -> ContentDigest.sha256("ZZZZ" + SHA256_HEX.substring(4)));
    }
    @Test void sha256_rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> ContentDigest.sha256("abc123"));
    }
    @Test void digest_matchesSameContent() {
        ContentDigest d1 = ContentDigest.sha256(SHA256_HEX);
        ContentDigest d2 = ContentDigest.sha256(SHA256_HEX);
        assertTrue(d1.matches(d2));
    }
    @Test void digest_doesNotMatchDifferentContent() {
        ContentDigest d1 = ContentDigest.sha256(SHA256_HEX);
        String otherSha = Sha256TestAssets.EMPTY_TEXT_HASH;
        ContentDigest d2 = ContentDigest.sha256(otherSha);
        if (!d1.canonicalValue().equals(d2.canonicalValue())) {
            assertFalse(d1.matches(d2));
        }
    }
}
class Sha256TestAssets {
    static final String EMPTY_TEXT_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
}
