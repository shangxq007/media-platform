package com.example.platform.render.api;

import com.example.platform.render.app.preview.PreviewMediaUploadService;
import com.example.platform.render.api.request.PreviewUploadKey;
import com.example.platform.shared.web.TenantGuard;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Independent preview upload coordinates Storage/Product, never creates or executes a RenderJob. */
@RestController
@RequestMapping("/api/preview/media")
public class PreviewMediaUploadController {
    private final PreviewMediaUploadService uploads;
    public PreviewMediaUploadController(PreviewMediaUploadService uploads) { this.uploads=uploads; }
    @PostMapping
    public Map<String,String> upload(@RequestParam("file") MultipartFile file,
            @RequestHeader(value="Idempotency-Key",required=false) String requestKey,
            @RequestHeader(value="X-Tenant-ID",required=false) String tenantHint,
            jakarta.servlet.http.HttpServletResponse response) {
        if(tenantHint!=null && !tenantHint.equals(TenantGuard.requireTenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Preview tenant header must match authenticated tenant");
        if(file.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Preview media is empty");
        if(!"video/mp4".equals(file.getContentType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only video/mp4 is supported");
        if(file.getSize()>20*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File too large (max 20MB)");
        PreviewUploadKey key;
        try {key=new PreviewUploadKey(requestKey==null?java.util.UUID.randomUUID().toString():requestKey);}
        catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid preview request key",invalid);}
        response.setHeader("Idempotency-Key",key.value());
        try {
            var result=uploads.upload(key,file.getBytes(),file.getContentType());
            return Map.of("mediaId",result.mediaId(),"size",Long.toString(result.size()));
        } catch(java.io.IOException failed){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Failed to read preview media",failed);}
    }
}
