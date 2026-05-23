package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDefinition;
import com.example.platform.policy.featureflag.domain.FeatureFlagTargetingRule;
import com.example.platform.policy.featureflag.domain.FeatureFlagType;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class FeatureFlagJdbcStore implements FeatureFlagPersistence {

    private final JdbcTemplate jdbc;

    public FeatureFlagJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FeatureFlagDefinition> findByKey(String flagKey) {
        List<FeatureFlagDefinition> rows = jdbc.query(
                "SELECT * FROM feature_flag_definition WHERE flag_key = ?",
                this::mapDefinition,
                flagKey);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        FeatureFlagDefinition def = rows.get(0);
        List<FeatureFlagTargetingRule> rules = findRules(flagKey);
        return Optional.of(copyWithRules(def, rules));
    }

    public List<FeatureFlagDefinition> findAll() {
        return jdbc.query(
                "SELECT * FROM feature_flag_definition ORDER BY flag_key",
                this::mapDefinition).stream()
                .map(def -> copyWithRules(def, findRules(def.flagKey())))
                .toList();
    }

    public FeatureFlagDefinition save(FeatureFlagDefinition definition) {
        String id = jdbc.query(
                "SELECT id FROM feature_flag_definition WHERE flag_key = ?",
                rs -> rs.next() ? rs.getString("id") : null,
                definition.flagKey());
        if (id == null) {
            id = Ids.newId("ffd");
            jdbc.update("""
                    INSERT INTO feature_flag_definition
                    (id, flag_key, name, description, flag_type, enabled, default_value_json,
                     variants_json, tags_json, owner, archived, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    definition.flagKey(),
                    definition.name(),
                    definition.description(),
                    definition.flagType().name(),
                    definition.enabled(),
                    Jsons.toJson(definition.defaultValue()),
                    Jsons.toJson(definition.variants()),
                    Jsons.toJson(definition.tags()),
                    definition.owner(),
                    definition.archived(),
                    Timestamp.from(definition.createdAt() != null ? definition.createdAt() : Instant.now()),
                    Timestamp.from(definition.updatedAt() != null ? definition.updatedAt() : Instant.now()));
        } else {
            jdbc.update("""
                    UPDATE feature_flag_definition SET
                    name = ?, description = ?, flag_type = ?, enabled = ?, default_value_json = ?,
                    variants_json = ?, tags_json = ?, owner = ?, archived = ?, updated_at = ?
                    WHERE flag_key = ?
                    """,
                    definition.name(),
                    definition.description(),
                    definition.flagType().name(),
                    definition.enabled(),
                    Jsons.toJson(definition.defaultValue()),
                    Jsons.toJson(definition.variants()),
                    Jsons.toJson(definition.tags()),
                    definition.owner(),
                    definition.archived(),
                    Timestamp.from(Instant.now()),
                    definition.flagKey());
        }
        clearRules(definition.flagKey());
        if (definition.targetingRules() != null) {
            definition.targetingRules().forEach(r -> saveRule(definition.flagKey(), r));
        }
        return findByKey(definition.flagKey()).orElse(definition);
    }

    public boolean delete(String flagKey) {
        clearRules(flagKey);
        return jdbc.update("DELETE FROM feature_flag_definition WHERE flag_key = ?", flagKey) > 0;
    }

    public void saveRule(String flagKey, FeatureFlagTargetingRule rule) {
        String ruleId = rule.ruleId() != null ? rule.ruleId() : Ids.newId("ffr");
        jdbc.update("""
                INSERT INTO feature_flag_targeting_rule
                (id, flag_key, rule_id, tenant_id, workspace_id, user_id, role, tier, percentage, priority, enabled, rule_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Ids.newId("fftr"),
                flagKey,
                ruleId,
                rule.tenantId(),
                rule.workspaceId(),
                rule.userId(),
                rule.role(),
                rule.tier(),
                rule.percentage(),
                rule.priority() != null ? rule.priority() : 0,
                rule.enabled(),
                Jsons.toJson(rule),
                Timestamp.from(Instant.now()));
    }

    public List<FeatureFlagTargetingRule> findRules(String flagKey) {
        return jdbc.query(
                "SELECT rule_json FROM feature_flag_targeting_rule WHERE flag_key = ? ORDER BY priority",
                (rs, rowNum) -> Jsons.fromJson(rs.getString("rule_json"), FeatureFlagTargetingRule.class),
                flagKey);
    }

    public void clearRules(String flagKey) {
        jdbc.update("DELETE FROM feature_flag_targeting_rule WHERE flag_key = ?", flagKey);
    }

    private FeatureFlagDefinition mapDefinition(ResultSet rs, int rowNum) throws SQLException {
        String flagType = rs.getString("flag_type");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new FeatureFlagDefinition(
                rs.getString("flag_key"),
                rs.getString("name"),
                rs.getString("description"),
                flagType != null ? FeatureFlagType.valueOf(flagType) : FeatureFlagType.BOOLEAN,
                parseDefaultValue(rs.getString("default_value_json")),
                Jsons.fromJsonList(rs.getString("variants_json"), com.example.platform.policy.featureflag.domain.FeatureFlagVariant.class),
                new ArrayList<>(),
                rs.getBoolean("enabled"),
                rs.getString("owner"),
                Jsons.fromJsonList(rs.getString("tags_json"), String.class),
                created != null ? created.toInstant() : Instant.now(),
                updated != null ? updated.toInstant() : Instant.now(),
                rs.getBoolean("archived"));
    }

    private static Object parseDefaultValue(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        return Jsons.fromJson(json, Object.class);
    }

    private static FeatureFlagDefinition copyWithRules(FeatureFlagDefinition def, List<FeatureFlagTargetingRule> rules) {
        return new FeatureFlagDefinition(
                def.flagKey(), def.name(), def.description(), def.flagType(), def.defaultValue(),
                def.variants(), rules, def.enabled(), def.owner(), def.tags(),
                def.createdAt(), def.updatedAt(), def.archived());
    }
}
