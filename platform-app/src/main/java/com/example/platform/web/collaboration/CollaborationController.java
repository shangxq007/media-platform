package com.example.platform.web.collaboration;

import com.example.platform.shared.authorization.FailClosedAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/me/shared-resources")
public class CollaborationController {

    private final SharedResourceService sharedResourceService;

    public CollaborationController(SharedResourceService sharedResourceService) {
        this.sharedResourceService = sharedResourceService;
    }

    @PostMapping("/grants")
    public ResponseEntity<Map<String, Object>> grantAccess(
            @RequestBody GrantSharedResourceRequest body,
            HttpServletRequest req) {
        throw FailClosedAuthorization.unavailable("collaboration grant creation");
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<Map<String, Object>> revokeMyGrant(@PathVariable String grantId) {
        throw FailClosedAuthorization.unavailable("collaboration grant revocation");
    }

    public record GrantSharedResourceRequest(
            String tenantId,
            String resourceType,
            String resourceId,
            String resourceName,
            String sharedByUserId,
            String sharedWithUserId,
            String permission) {}
}
