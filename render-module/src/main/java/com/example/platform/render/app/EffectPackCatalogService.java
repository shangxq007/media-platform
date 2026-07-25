package com.example.platform.render.app;

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
import java.time.LocalDateTime;
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
import static com.example.platform.typedschema.jooq.generated.tables.EffectPack.EFFECT_PACK;
import static com.example.platform.typedschema.jooq.generated.tables.EffectPackEffect.EFFECT_PACK_EFFECT;


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
                .from(EFFECT_PACK)
                .where(EFFECT_PACK.PACK_ID.eq(BUILTIN_PACK_ID))
                .and(EFFECT_PACK.BUILTIN.eq(true))
                .fetchOne(0, int.class);
        if (count != null && count > 0) {
            syncMappingFromDatabase();
            return;
        }
        String rowId = Ids.newId("epk");
        List<String> tiers = List.of("FREE", "PRO", "TEAM", "ENTERPRISE");
        dsl.insertInto(EFFECT_PACK)
                .columns(EFFECT_PACK.ID, EFFECT_PACK.PACK_ID, EFFECT_PACK.VERSION, EFFECT_PACK.NAME,
                        EFFECT_PACK.DESCRIPTION, EFFECT_PACK.AUTHOR, EFFECT_PACK.COMPATIBILITY,
                        EFFECT_PACK.ALLOWED_TIERS, EFFECT_PACK.TENANT_ID, EFFECT_PACK.BUILTIN,
                        EFFECT_PACK.CREATED_AT, EFFECT_PACK.UPDATED_AT)
                .values(rowId, BUILTIN_PACK_ID, BUILTIN_VERSION, "Core Effects",
                        "Built-in core effects pack", "media-platform", "2.0",
                        writeJson(tiers), "", true, LocalDateTime.now(), LocalDateTime.now())
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
                .from(EFFECT_PACK)
                .where(EFFECT_PACK.BUILTIN.eq(true)
                        .or(EFFECT_PACK.TENANT_ID.eq(tenantId != null ? tenantId : "")))
                .orderBy(EFFECT_PACK.BUILTIN.desc(), EFFECT_PACK.PACK_ID, EFFECT_PACK.VERSION.desc())
                .fetch();
        List<EffectPackDto> packs = new ArrayList<>();
        for (Record row : rows) {
            packs.add(toPackDto(row));
        }
        return packs;
    }

    public Optional<EffectPackDto> getPack(String packId, String version, String tenantId) {
        Record row = dsl.select()
                .from(EFFECT_PACK)
                .where(EFFECT_PACK.PACK_ID.eq(packId))
                .and(EFFECT_PACK.VERSION.eq(version))
                .and(EFFECT_PACK.BUILTIN.eq(true).or(EFFECT_PACK.TENANT_ID.eq(tenantId)))
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
        dsl.insertInto(EFFECT_PACK)
                .columns(EFFECT_PACK.ID, EFFECT_PACK.PACK_ID, EFFECT_PACK.VERSION, EFFECT_PACK.NAME,
                        EFFECT_PACK.DESCRIPTION, EFFECT_PACK.AUTHOR, EFFECT_PACK.COMPATIBILITY,
                        EFFECT_PACK.ALLOWED_TIERS, EFFECT_PACK.TENANT_ID, EFFECT_PACK.BUILTIN,
                        EFFECT_PACK.CREATED_AT, EFFECT_PACK.UPDATED_AT)
                .values(rowId, request.packId(), version, request.name(),
                        request.description(), request.author(),
                        request.compatibility() != null ? request.compatibility() : "2.0",
                        writeJson(request.allowedTiers()), tenantId, false,
                        LocalDateTime.now(), LocalDateTime.now())
                .execute();
        replaceEffects(rowId, request.effects());
        syncMappingFromDatabase();
        return getPack(request.packId(), version, tenantId).orElseThrow();
    }

    @Transactional
    public EffectPackDto updateCustomPack(String tenantId, String packId, String version,
                                          UpdateEffectPackRequest request) {
        Record row = findOwnedPackRow(packId, version, tenantId);
        dsl.update(EFFECT_PACK)
                .set(EFFECT_PACK.NAME, request.name())
                .set(EFFECT_PACK.DESCRIPTION, request.description())
                .set(EFFECT_PACK.AUTHOR, request.author())
                .set(EFFECT_PACK.COMPATIBILITY, request.compatibility())
                .set(EFFECT_PACK.ALLOWED_TIERS, writeJson(request.allowedTiers()))
                .set(EFFECT_PACK.UPDATED_AT, LocalDateTime.now())
                .where(EFFECT_PACK.ID.eq(row.get(EFFECT_PACK.ID, String.class)))
                .execute();
        replaceEffects(row.get(EFFECT_PACK.ID, String.class), request.effects());
        syncMappingFromDatabase();
        return getPack(packId, version, tenantId).orElseThrow();
    }

    @Transactional
    public void deleteCustomPack(String tenantId, String packId, String version) {
        Record row = findOwnedPackRow(packId, version, tenantId);
        String rowId = row.get(EFFECT_PACK.ID, String.class);
        dsl.deleteFrom(EFFECT_PACK_EFFECT)
                .where(EFFECT_PACK_EFFECT.PACK_ROW_ID.eq(rowId))
                .execute();
        dsl.deleteFrom(EFFECT_PACK)
                .where(EFFECT_PACK.ID.eq(rowId))
                .execute();
        syncMappingFromDatabase();
    }

    public void syncMappingFromDatabase() {
        effectMapping.reloadFromCatalog(loadAllEffectRows());
    }

    private Record findOwnedPackRow(String packId, String version, String tenantId) {
        Record row = dsl.select()
                .from(EFFECT_PACK)
                .where(EFFECT_PACK.PACK_ID.eq(packId))
                .and(EFFECT_PACK.VERSION.eq(version))
                .and(EFFECT_PACK.TENANT_ID.eq(tenantId))
                .and(EFFECT_PACK.BUILTIN.eq(false))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Custom effect pack not found: " + packId + "@" + version);
        }
        return row;
    }

    private void replaceEffects(String packRowId, List<EffectPackEffectDto> effects) {
        dsl.deleteFrom(EFFECT_PACK_EFFECT)
                .where(EFFECT_PACK_EFFECT.PACK_ROW_ID.eq(packRowId))
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
        dsl.insertInto(EFFECT_PACK_EFFECT)
                .columns(EFFECT_PACK.ID, EFFECT_PACK_EFFECT.PACK_ROW_ID, EFFECT_PACK_EFFECT.EFFECT_KEY,
                        EFFECT_PACK_EFFECT.DISPLAY_NAME, EFFECT_PACK_EFFECT.CATEGORY, EFFECT_PACK.DESCRIPTION,
                        EFFECT_PACK_EFFECT.PARAMETER_SCHEMA, EFFECT_PACK_EFFECT.DEFAULT_VALUES,
                        EFFECT_PACK_EFFECT.PROVIDER_MAPPINGS, EFFECT_PACK.ALLOWED_TIERS, EFFECT_PACK_EFFECT.SORT_ORDER)
                .values(Ids.newId("efx"), packRowId, descriptor.effectKey(),
                        descriptor.displayName(), descriptor.category(), descriptor.description(),
                        writeJson(schema), writeJson(descriptor.defaultParams()),
                        writeJson(descriptor.providerKeys()), writeJson(defaultTiers), sortOrder)
                .execute();
    }

    private void insertEffectFromDto(String packRowId, EffectPackEffectDto effect, int sortOrder) {
        dsl.insertInto(EFFECT_PACK_EFFECT)
                .columns(EFFECT_PACK.ID, EFFECT_PACK_EFFECT.PACK_ROW_ID, EFFECT_PACK_EFFECT.EFFECT_KEY,
                        EFFECT_PACK_EFFECT.DISPLAY_NAME, EFFECT_PACK_EFFECT.CATEGORY, EFFECT_PACK.DESCRIPTION,
                        EFFECT_PACK_EFFECT.PARAMETER_SCHEMA, EFFECT_PACK_EFFECT.DEFAULT_VALUES,
                        EFFECT_PACK_EFFECT.PROVIDER_MAPPINGS, EFFECT_PACK.ALLOWED_TIERS, EFFECT_PACK_EFFECT.SORT_ORDER)
                .values(Ids.newId("efx"), packRowId, effect.effectKey(),
                        effect.displayName(), effect.category(), effect.description(),
                        writeJson(effect.parameterSchema()), writeJson(effect.defaultValues()),
                        writeJson(effect.providerMappings()), writeJson(effect.allowedTiers()),
                        sortOrder)
                .execute();
    }

    private List<EffectPackEffectDto> loadAllEffectRows() {
        List<Record> rows = dsl.select()
                .from(EFFECT_PACK_EFFECT)
                .orderBy(EFFECT_PACK_EFFECT.SORT_ORDER)
                .fetch();
        List<EffectPackEffectDto> effects = new ArrayList<>();
        for (Record row : rows) {
            effects.add(new EffectPackEffectDto(
                    row.get(EFFECT_PACK_EFFECT.EFFECT_KEY, String.class),
                    row.get(EFFECT_PACK_EFFECT.DISPLAY_NAME, String.class),
                    row.get(EFFECT_PACK_EFFECT.CATEGORY, String.class),
                    row.get(EFFECT_PACK.DESCRIPTION, String.class),
                    readMap(row.get(EFFECT_PACK_EFFECT.PARAMETER_SCHEMA, String.class)),
                    readMap(row.get(EFFECT_PACK_EFFECT.DEFAULT_VALUES, String.class)),
                    readStringList(row.get(EFFECT_PACK_EFFECT.PROVIDER_MAPPINGS, String.class)),
                    readStringList(row.get(EFFECT_PACK.ALLOWED_TIERS, String.class)),
                    row.get(EFFECT_PACK_EFFECT.TAXONOMY_CATEGORY, String.class),
                    row.get(EFFECT_PACK_EFFECT.IS_EFFECT, Boolean.class)));
        }
        return effects;
    }

    private EffectPackDto toPackDto(Record row) {
        String rowId = row.get(EFFECT_PACK.ID, String.class);
        List<Record> effectRows = dsl.select()
                .from(EFFECT_PACK_EFFECT)
                .where(EFFECT_PACK_EFFECT.PACK_ROW_ID.eq(rowId))
                .orderBy(EFFECT_PACK_EFFECT.SORT_ORDER)
                .fetch();
        List<EffectPackEffectDto> effects = new ArrayList<>();
        for (Record effectRow : effectRows) {
            effects.add(new EffectPackEffectDto(
                    effectRow.get(EFFECT_PACK_EFFECT.EFFECT_KEY, String.class),
                    effectRow.get(EFFECT_PACK_EFFECT.DISPLAY_NAME, String.class),
                    effectRow.get(EFFECT_PACK_EFFECT.CATEGORY, String.class),
                    effectRow.get(EFFECT_PACK.DESCRIPTION, String.class),
                    readMap(effectRow.get(EFFECT_PACK_EFFECT.PARAMETER_SCHEMA, String.class)),
                    readMap(effectRow.get(EFFECT_PACK_EFFECT.DEFAULT_VALUES, String.class)),
                    readStringList(effectRow.get(EFFECT_PACK_EFFECT.PROVIDER_MAPPINGS, String.class)),
                    readStringList(effectRow.get(EFFECT_PACK.ALLOWED_TIERS, String.class)),
                    effectRow.get(EFFECT_PACK_EFFECT.TAXONOMY_CATEGORY, String.class),
                    effectRow.get(EFFECT_PACK_EFFECT.IS_EFFECT, Boolean.class)));
        }
        return new EffectPackDto(
                row.get(EFFECT_PACK.PACK_ID, String.class),
                row.get(EFFECT_PACK.VERSION, String.class),
                row.get(EFFECT_PACK.NAME, String.class),
                row.get(EFFECT_PACK.DESCRIPTION, String.class),
                row.get(EFFECT_PACK.AUTHOR, String.class),
                row.get(EFFECT_PACK.COMPATIBILITY, String.class),
                readStringList(row.get(EFFECT_PACK.ALLOWED_TIERS, String.class)),
                row.get(EFFECT_PACK.BUILTIN, Boolean.class),
                row.get(EFFECT_PACK.TENANT_ID, String.class),
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
