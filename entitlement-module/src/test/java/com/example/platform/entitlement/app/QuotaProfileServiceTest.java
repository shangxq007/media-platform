package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.QuotaProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class QuotaProfileServiceTest {

    private QuotaProfileService service;

    @BeforeEach
    void setUp() {
        service = new QuotaProfileService(null, null);
    }

    @Test
    void createProfileReturnsProfileWithId() {
        QuotaProfile result = service.createProfile(
                "pro-quota", "Pro Quota", "Professional quota limits",
                300, 50, 3, 10737418240L, 100, 10,
                1000, 50, 120, 60, "admin");

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("pro-quota", result.profileKey());
        assertEquals("Pro Quota", result.name());
        assertEquals(300, result.monthlyRenderMinutes());
        assertEquals(3, result.concurrentRenderJobs());
    }

    @Test
    void getProfileReturnsEmptyWithoutRepository() {
        Optional<QuotaProfile> result = service.getProfile("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void listProfilesReturnsEmptyWithoutRepository() {
        List<QuotaProfile> result = service.listProfiles();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateProfileThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.updateProfile("key", "name", "desc",
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "admin"));
    }

    @Test
    void createEnterpriseProfile() {
        QuotaProfile result = service.createProfile(
                "enterprise-quota", "Enterprise Quota", "Enterprise quota limits",
                6000, 500, 50, 1099511627776L, 1000, 1000,
                999999, 9999, 600, 300, "admin");

        assertNotNull(result);
        assertEquals(6000, result.monthlyRenderMinutes());
        assertEquals(50, result.concurrentRenderJobs());
        assertEquals(1099511627776L, result.storageBytes());
    }
}
