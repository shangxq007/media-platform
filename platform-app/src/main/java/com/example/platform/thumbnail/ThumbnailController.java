package com.example.platform.thumbnail;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/thumbnails")
public class ThumbnailController {
    private final ThumbnailService service; private final ThumbnailArtifactReadService reads;
    public ThumbnailController(ThumbnailService service,ThumbnailArtifactReadService reads){this.service=service;this.reads=reads;}
    @PostMapping
    public ThumbnailContracts.Result submit(@PathVariable String tenantId,@PathVariable String projectId,@RequestBody Submit body){
        return service.submit(new ThumbnailContracts.Request(tenantId,projectId,body.sourceAssetId(),body.timestampSeconds(),body.imageFormat(),body.width(),body.quality(),body.idempotencyKey()));
    }
    @GetMapping("/{taskId}") public ThumbnailContracts.Result status(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String taskId){return service.status(tenantId,projectId,taskId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/{taskId}/image") public ResponseEntity<byte[]> image(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String taskId){var image=reads.read(tenantId,projectId,taskId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header("X-Artifact-Id",image.artifactId()).body(image.bytes());}
    public record Submit(String sourceAssetId,double timestampSeconds,String imageFormat,Integer width,Integer quality,String idempotencyKey){}
}
