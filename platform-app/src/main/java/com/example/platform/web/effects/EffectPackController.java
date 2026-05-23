package com.example.platform.web.effects;

import com.example.platform.render.app.EffectPackCatalogService;
import com.example.platform.render.app.dto.EffectPackDtos.CreateEffectPackRequest;
import com.example.platform.render.app.dto.EffectPackDtos.EffectPackDto;
import com.example.platform.render.app.dto.EffectPackDtos.UpdateEffectPackRequest;
import com.example.platform.shared.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/effect-packs")
@Tag(name = "Effect Packs", description = "特效包目录与管理")
public class EffectPackController {

    private final EffectPackCatalogService catalogService;

    public EffectPackController(EffectPackCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "列出可用特效包", description = "包含系统内置包与当前租户自定义包")
    public List<EffectPackDto> listPacks() {
        return catalogService.listPacks(TenantContext.get());
    }

    @GetMapping("/{packId}/versions/{version}")
    @Operation(summary = "获取特效包详情")
    public EffectPackDto getPack(@PathVariable String packId, @PathVariable String version) {
        return catalogService.getPack(packId, version, TenantContext.get())
                .orElseThrow(() -> new IllegalArgumentException("Effect pack not found"));
    }

    @PostMapping
    @Operation(summary = "创建租户自定义特效包")
    public ResponseEntity<EffectPackDto> createPack(@Valid @RequestBody CreateEffectPackRequest request) {
        EffectPackDto created = catalogService.createCustomPack(TenantContext.get(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{packId}/versions/{version}")
    @Operation(summary = "更新租户自定义特效包")
    public EffectPackDto updatePack(@PathVariable String packId, @PathVariable String version,
                                    @Valid @RequestBody UpdateEffectPackRequest request) {
        return catalogService.updateCustomPack(TenantContext.get(), packId, version, request);
    }

    @DeleteMapping("/{packId}/versions/{version}")
    @Operation(summary = "删除租户自定义特效包")
    public ResponseEntity<Void> deletePack(@PathVariable String packId, @PathVariable String version) {
        catalogService.deleteCustomPack(TenantContext.get(), packId, version);
        return ResponseEntity.noContent().build();
    }
}
