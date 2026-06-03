package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.app.dto.EffectPackDtos.CreateEffectPackRequest;
import com.example.platform.render.app.dto.EffectPackDtos.EffectPackDto;
import com.example.platform.render.app.dto.EffectPackDtos.EffectPackEffectDto;
import com.example.platform.render.app.dto.EffectPackDtos.UpdateEffectPackRequest;
import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.EffectParameterSchema;
import com.example.platform.shared.Ids;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EffectPackCatalogService {

    private static final Logger log = LoggerFactory.getLogger(EffectPackCatalogService.class);
    private static final String BUILTIN_PACK_ID = "builtin-core";
    private static final String BUILTIN_VERSION = "2.0.0";

    private final DSLContext dsl;
    private final EffectMappingService effectMapping;
    private final ObjectMapper objectMapper;

    public EffectPackCatalogService(DSLContext dsl, EffectMappingService effectMapping) {
        this.dsl = dsl;
        this.effectMapping = effectMapping;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void seedBuiltinPackIfAbsent() {
        Integer count = dsl.selectCount()
                .from(table("effect_pack"))
                .where(field("pack_id").eq(BUILTIN_PACK_ID))
                .and(field("builtin").eq(true))
                .fetchOne(0, int.class);
        if (count != null && count > 0) {
            syncMappingFromDatabase();
            return;
        }
        String rowId = Ids.newId("epk");
        List<String> tiers = List.of("FREE", "PRO", "TEAM", "ENTERPRISE");
        dsl.insertInto(table("effect_pack"))
                .columns(field("id"), field("pack_id"), field("version"), field("name"),
                        field("description"), field("author"), field("compatibility"),
                        field("allowed_tiers"), field("tenant_id"), field("builtin"),
                        field("created_at"), field("updated_at"))
                .values(rowId, BUILTIN_PACK_ID, BUILTIN_VERSION, "Core Effects",
                        "Built-in core effects pack", "media-platform", "2.0",
                        writeJson(tiers), "", true, OffsetDateTime.now(), OffsetDateTime.now())
                .execute();

        int order = 0;
        for (EffectDescriptor descriptor : effectMapping.getAllDescriptors()) {
            insertEffectRow(rowId, descriptor, tiers, order++);
        }
        log.info("Seeded builtin effect pack {} v{}", BUILTIN_PACK_ID, BUILTIN_VERSION);
        syncMappingFromDatabase();
    }

    public List<EffectPackDto> listPacks(String tenantId) {
        List<Record> rows = dsl.select()
                .from(table("effect_pack"))
                .where(field("builtin").eq(true)
                        .or(field("tenant_id").eq(tenantId != null ? tenantId : "")))
                .orderBy(field("builtin").desc(), field("pack_id"), field("version").desc())
                .fetch();
        List<EffectPackDto> packs = new ArrayList<>();
        for (Record row : rows) {
            packs.add(toPackDto(row));
        }
        return packs;
    }

    public Optional<EffectPackDto> getPack(String packId, String version, String tenantId) {
        Record row = dsl.select()
                .from(table("effect_pack"))
                .where(field("pack_id").eq(packId))
                .and(field("version").eq(version))
                .and(field("builtin").eq(true).or(field("tenant_id").eq(tenantId)))
                .fetchOne();
        return row == null ? Optional.empty() : Optional.of(toPackDto(row));
    }

    @Transactional
    public EffectPackDto createCustomPack(String tenantId, CreateEffectPackRequest request) {
        if (request.packId() == null || request.packId().isBlank()) {
            throw new IllegalArgumentException("packId is required");
        }
        if (BUILTIN_PACK_ID.equals(request.packId())) {
            throw new IllegalArgumentException("Cannot create pack with reserved id: " + BUILTIN_PACK_ID);
        }
        String rowId = Ids.newId("epk");
        String version = request.version() != null ? request.version() : "1.0.0";
        dsl.insertInto(table("effect_pack"))
                .columns(field("id"), field("pack_id"), field("version"), field("name"),
                        field("description"), field("author"), field("compatibility"),
                        field("allowed_tiers"), field("tenant_id"), field("builtin"),
                        field("created_at"), field("updated_at"))
                .values(rowId, request.packId(), version, request.name(),
                        request.description(), request.author(),
                        request.compatibility() != null ? request.compatibility() : "2.0",
                        writeJson(request.allowedTiers()), tenantId, false,
                        OffsetDateTime.now(), OffsetDateTime.now())
                .execute();
        replaceEffects(rowId, request.effects());
        syncMappingFromDatabase();
        return getPack(request.packId(), version, tenantId).orElseThrow();
    }

    @Transactional
    public EffectPackDto updateCustomPack(String tenantId, String packId, String version,
                                          UpdateEffectPackRequest request) {
        Record row = findOwnedPackRow(packId, version, tenantId);
        dsl.update(table("effect_pack"))
                .set(field("name"), request.name())
                .set(field("description"), request.description())
                .set(field("author"), request.author())
                .set(field("compatibility"), request.compatibility())
                .set(field("allowed_tiers"), writeJson(request.allowedTiers()))
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(row.get(field("id"), String.class)))
                .execute();
        replaceEffects(row.get(field("id"), String.class), request.effects());
        syncMappingFromDatabase();
        return getPack(packId, version, tenantId).orElseThrow();
    }

    @Transactional
    public void deleteCustomPack(String tenantId, String packId, String version) {
        Record row = findOwnedPackRow(packId, version, tenantId);
        String rowId = row.get(field("id"), String.class);
        dsl.deleteFrom(table("effect_pack_effect"))
                .where(field("pack_row_id").eq(rowId))
                .execute();
        dsl.deleteFrom(table("effect_pack"))
                .where(field("id").eq(rowId))
                .execute();
        syncMappingFromDatabase();
    }

    public void syncMappingFromDatabase() {
        effectMapping.reloadFromCatalog(loadAllEffectRows());
    }

    private Record findOwnedPackRow(String packId, String version, String tenantId) {
        Record row = dsl.select()
                .from(table("effect_pack"))
                .where(field("pack_id").eq(packId))
                .and(field("version").eq(version))
                .and(field("tenant_id").eq(tenantId))
                .and(field("builtin").eq(false))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Custom effect pack not found: " + packId + "@" + version);
        }
        return row;
    }

    private void replaceEffects(String packRowId, List<EffectPackEffectDto> effects) {
        dsl.deleteFrom(table("effect_pack_effect"))
                .where(field("pack_row_id").eq(packRowId))
                .execute();
        if (effects == null) {
            return;
        }
        int order = 0;
        for (EffectPackEffectDto effect : effects) {
            insertEffectFromDto(packRowId, effect, order++);
        }
    }

    private void insertEffectRow(String packRowId, EffectDescriptor descriptor,
                                 List<String> defaultTiers, int sortOrder) {
        Map<String, Object> schema = new LinkedHashMap<>();
        for (EffectParameterSchema param : descriptor.paramSchemas()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", param.type());
            def.put("defaultValue", param.defaultValue());
            if (param.min() != null) {
                def.put("min", param.min());
            }
            if (param.max() != null) {
                def.put("max", param.max());
            }
            def.put("description", param.description());
            schema.put(param.name(), def);
        }
        dsl.insertInto(table("effect_pack_effect"))
                .columns(field("id"), field("pack_row_id"), field("effect_key"),
                        field("display_name"), field("category"), field("description"),
                        field("parameter_schema"), field("default_values"),
                        field("provider_mappings"), field("allowed_tiers"), field("sort_order"))
                .values(Ids.newId("efx"), packRowId, descriptor.effectKey(),
                        descriptor.displayName(), descriptor.category(), descriptor.description(),
                        writeJson(schema), writeJson(descriptor.defaultParams()),
                        writeJson(descriptor.providerKeys()), writeJson(defaultTiers), sortOrder)
                .execute();
    }

    private void insertEffectFromDto(String packRowId, EffectPackEffectDto effect, int sortOrder) {
        dsl.insertInto(table("effect_pack_effect"))
                .columns(field("id"), field("pack_row_id"), field("effect_key"),
                        field("display_name"), field("category"), field("description"),
                        field("parameter_schema"), field("default_values"),
                        field("provider_mappings"), field("allowed_tiers"), field("sort_order"))
                .values(Ids.newId("efx"), packRowId, effect.effectKey(),
                        effect.displayName(), effect.category(), effect.description(),
                        writeJson(effect.parameterSchema()), writeJson(effect.defaultValues()),
                        writeJson(effect.providerMappings()), writeJson(effect.allowedTiers()),
                        sortOrder)
                .execute();
    }

    private List<EffectPackEffectDto> loadAllEffectRows() {
        List<Record> rows = dsl.select()
                .from(table("effect_pack_effect"))
                .orderBy(field("sort_order"))
                .fetch();
        List<EffectPackEffectDto> effects = new ArrayList<>();
        for (Record row : rows) {
            effects.add(new EffectPackEffectDto(
                    row.get(field("effect_key"), String.class),
                    row.get(field("display_name"), String.class),
                    row.get(field("category"), String.class),
                    row.get(field("description"), String.class),
                    readMap(row.get(field("parameter_schema"), String.class)),
                    readMap(row.get(field("default_values"), String.class)),
                    readStringList(row.get(field("provider_mappings"), String.class)),
                    readStringList(row.get(field("allowed_tiers"), String.class)),
                    row.get(field("taxonomy_category"), String.class),
                    row.get(field("is_effect"), Boolean.class)));
        }
        return effects;
    }

    private EffectPackDto toPackDto(Record row) {
        String rowId = row.get(field("id"), String.class);
        List<Record> effectRows = dsl.select()
                .from(table("effect_pack_effect"))
                .where(field("pack_row_id").eq(rowId))
                .orderBy(field("sort_order"))
                .fetch();
        List<EffectPackEffectDto> effects = new ArrayList<>();
        for (Record effectRow : effectRows) {
            effects.add(new EffectPackEffectDto(
                    effectRow.get(field("effect_key"), String.class),
                    effectRow.get(field("display_name"), String.class),
                    effectRow.get(field("category"), String.class),
                    effectRow.get(field("description"), String.class),
                    readMap(effectRow.get(field("parameter_schema"), String.class)),
                    readMap(effectRow.get(field("default_values"), String.class)),
                    readStringList(effectRow.get(field("provider_mappings"), String.class)),
                    readStringList(effectRow.get(field("allowed_tiers"), String.class)),
                    effectRow.get(field("taxonomy_category"), String.class),
                    effectRow.get(field("is_effect"), Boolean.class)));
        }
        return new EffectPackDto(
                row.get(field("pack_id"), String.class),
                row.get(field("version"), String.class),
                row.get(field("name"), String.class),
                row.get(field("description"), String.class),
                row.get(field("author"), String.class),
                row.get(field("compatibility"), String.class),
                readStringList(row.get(field("allowed_tiers"), String.class)),
                row.get(field("builtin"), Boolean.class),
                row.get(field("tenant_id"), String.class),
                effects);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : List.of());
        } catch (Exception e) {
            throw new IllegalStateException("JSON write failed", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
