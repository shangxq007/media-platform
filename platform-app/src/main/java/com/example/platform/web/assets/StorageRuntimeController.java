package com.example.platform.web.assets;

import com.example.platform.render.app.storage.StorageRuntimeService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageRuntimeController {

    private final StorageRuntimeService service;
    public StorageRuntimeController(StorageRuntimeService service) { this.service = service; }

    @GetMapping("/{storageReferenceId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String storageReferenceId) {
            return service.find(storageReferenceId).map(r -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("storageReferenceId", r.storageReferenceId());
                m.put("providerType", r.providerType());
                m.put("path", r.absolutePath());
                m.put("checksum", r.checksum() != null ? r.checksum() : "");
                m.put("contentHash", r.contentHash() != null ? r.contentHash() : "");
                m.put("fileSize", r.fileSize());
                m.put("mimeType", r.mimeType() != null ? r.mimeType() : "");
                return ResponseEntity.ok(m);
            }).orElse(ResponseEntity.notFound().build());
    }
}
