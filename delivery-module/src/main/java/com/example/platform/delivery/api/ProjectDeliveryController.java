package com.example.platform.delivery.api;

import com.example.platform.delivery.app.DeliveryService;
import com.example.platform.delivery.app.DeliveryFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/deliveries")
public class ProjectDeliveryController {
    private final DeliveryService deliveries;
    public ProjectDeliveryController(DeliveryService deliveries){this.deliveries=deliveries;}
    public record Create(@NotBlank @Size(max=128) String sourceRenderJobId,@Size(max=128) String artifactId) {}
    @PostMapping
    public ResponseEntity<DeliveryService.View> create(@PathVariable String projectId,@Valid @RequestBody Create request,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        var result=deliveries.create(projectId,request.sourceRenderJobId(),request.artifactId(),key);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(result.delivery());
    }
    @GetMapping("/{deliveryId}")
    public DeliveryService.View get(@PathVariable String projectId,@PathVariable String deliveryId){return deliveries.get(projectId,deliveryId);}
    @GetMapping
    public List<DeliveryService.View> list(@PathVariable String projectId,@RequestParam(defaultValue="50") int limit){return deliveries.list(projectId,limit);}
    @GetMapping("/{deliveryId}/result")
    public ResponseEntity<byte[]> result(@PathVariable String projectId,@PathVariable String deliveryId) {
        var content=deliveries.result(projectId,deliveryId);byte[] bytes=content.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).contentLength(bytes.length)
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(content.fileName()).build().toString()).body(bytes);
    }
    @ExceptionHandler(DeliveryFailure.class)
    public ResponseEntity<Map<String,String>> failure(DeliveryFailure failure) {
        return ResponseEntity.status(failure.status()).header(HttpHeaders.CACHE_CONTROL,"no-store").body(Map.of("code",failure.code()));
    }
}
