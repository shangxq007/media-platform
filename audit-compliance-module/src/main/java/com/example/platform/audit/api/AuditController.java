package com.example.platform.audit.api;

import com.example.platform.audit.api.dto.CreateAuditRecordRequest;
import com.example.platform.audit.app.AuditCategory;
import com.example.platform.audit.app.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit/compliance")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @PostMapping("/records")
    public Map<String, String> create(@RequestBody CreateAuditRecordRequest request) {
        String id = service.record(
                request.actorType(),
                request.actorId(),
                request.action(),
                request.resourceType(),
                request.resourceId(),
                request.payload(),
                request.category()
        );
        return Map.of("id", id);
    }

    @GetMapping("/records")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "50") int limit) {
        return service.recent(Math.max(1, Math.min(limit, 200)));
    }

    @GetMapping("/records/category/{category}")
    public List<Map<String, Object>> byCategory(
            @PathVariable AuditCategory category,
            @RequestParam(defaultValue = "50") int limit) {
        return service.findByCategory(category, Math.max(1, Math.min(limit, 200)));
    }

    @GetMapping("/records/resource")
    public List<Map<String, Object>> byResource(
            @RequestParam String resourceType,
            @RequestParam String resourceId) {
        return service.findByResource(resourceType, resourceId);
    }
}
