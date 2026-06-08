package com.example.platform.secrets.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SecretRefTest {

    @Test
    void parsesVaultPathWithField() {
        SecretRef ref = SecretRef.parse("vault:delivery/tenants/t1/dst_1#password");
        assertEquals("vault", ref.backend());
        assertEquals("delivery/tenants/t1/dst_1", ref.path());
        assertEquals("password", ref.field());
        assertEquals("vault:delivery/tenants/t1/dst_1#password", ref.encode());
    }

    @Test
    void parsesEnvWithDefault() {
        SecretRef ref = SecretRef.parse("${env:MY_VAR:default}");
        assertEquals("env", ref.backend());
        assertEquals("MY_VAR:default", ref.path());
        assertNull(ref.field());
    }
}
