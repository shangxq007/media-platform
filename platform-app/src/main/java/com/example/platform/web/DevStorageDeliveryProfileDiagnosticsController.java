package com.example.platform.web;

import com.example.platform.storage.delivery.contract.StorageDeliveryProfileId;
import com.example.platform.storage.delivery.diagnostics.StorageDeliveryProfileDiagnosticsResponse;
import com.example.platform.storage.delivery.diagnostics.StorageDeliveryProfileDiagnosticsService;
import com.example.platform.storage.delivery.diagnostics.StorageDeliveryProfileDiagnosticsItem;
import com.example.platform.storage.delivery.diagnostics.StorageDeliveryProfileValidationDiagnostics;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal read-only diagnostics for storage delivery profiles.
 * GET only. No mutation. No provider selection. No signed URL generation.
 */
@RestController
@RequestMapping("/dev/storage-delivery-profiles")
public class DevStorageDeliveryProfileDiagnosticsController {

    private final StorageDeliveryProfileDiagnosticsService service;

    public DevStorageDeliveryProfileDiagnosticsController(StorageDeliveryProfileDiagnosticsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<StorageDeliveryProfileDiagnosticsResponse> getDiagnostics() {
        return ResponseEntity.ok(service.getDiagnostics());
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<StorageDeliveryProfileDiagnosticsItem> getProfileDiagnostics(@PathVariable String profileId) {
        Optional<StorageDeliveryProfileDiagnosticsItem> item = service.getProfileDiagnostics(new StorageDeliveryProfileId(profileId));
        return item.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/validation")
    public ResponseEntity<StorageDeliveryProfileValidationDiagnostics> getValidationDiagnostics() {
        return ResponseEntity.ok(service.getValidationDiagnostics());
    }
}
