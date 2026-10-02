package com.example.platform.thumbnail;

import com.example.platform.contract.media.ThumbnailContracts;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ThumbnailContractsTest {
    @Test void rejectsNonFiniteAndUnsupportedRequests() {
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailContracts.Request("t","p","a",Double.NaN,"jpeg",null,null,"k"));
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailContracts.Request("t","p","a",1,"gif",null,null,"k"));
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailContracts.Request("t","p","a",1,"jpeg",5000,null,"k"));
    }
    @Test void acceptsBoundedCanonicalRequest() {
        var r = new ThumbnailContracts.Request("tenant","project","asset",2.5,"PNG",640,90,"request-1");
        assertEquals("png",r.imageFormat()); assertEquals(640,r.width());
    }
}
