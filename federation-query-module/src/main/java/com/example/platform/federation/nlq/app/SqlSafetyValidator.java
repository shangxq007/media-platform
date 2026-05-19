package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.SqlSafetyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlSafetyValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlSafetyValidator.class);

    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*(SELECT|WITH)\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern DDL_PATTERN = Pattern.compile("\\b(CREATE|DROP|ALTER|TRUNCATE|RENAME)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DML_PATTERN = Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";\\s*[^\\s]");
    private static final Pattern STAR_PATTERN = Pattern.compile("\\bSELECT\\s+\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("\\b(WHERE|AND)\\s+\\w+\\s*(>=|<=|BETWEEN|>|<|=)\\s*['\"]?\\d{4}-\\d{2}-\\d{2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CROSS_JOIN_PATTERN = Pattern.compile("\\bCROSS\\s+JOIN\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REF_PATTERN = Pattern.compile("\\b(FROM|JOIN)\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\bSELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Set<String> sensitiveFields = Set.of(
        "output_url", "api_key", "secret", "password", "token",
        "credential", "private_key", "ssn", "credit_card", "email", "phone"
    );

    public SqlSafetyResult validate(String sql, Set<String> allowedDatasets) {
        List<String> violations = new ArrayList<>();
        String normalizedSql = normalize(sql);

        if (!SELECT_PATTERN.matcher(normalizedSql).find()) {
            violations.add("SQL must start with SELECT or WITH");
        }

        if (DDL_PATTERN.matcher(normalizedSql).find()) {
            violations.add("DDL statements are not allowed");
        }

        if (DML_PATTERN.matcher(normalizedSql).find()) {
            violations.add("DML statements (INSERT, UPDATE, DELETE) are not allowed");
        }

        if (SEMICOLON_PATTERN.matcher(normalizedSql).find()) {
            violations.add("Multi-statement queries are not allowed (semicolon injection)");
        }

        List<String> referencedDatasets = extractTableRefs(normalizedSql);
        List<String> unauthorizedDatasets = referencedDatasets.stream()
            .filter(ref -> allowedDatasets != null && !allowedDatasets.contains(ref))
            .toList();
        if (!unauthorizedDatasets.isEmpty()) {
            violations.add("Unauthorized dataset references: " + unauthorizedDatasets);
        }

        List<String> referencedFields = extractFieldRefs(normalizedSql);
        List<String> sensitiveRefs = referencedFields.stream()
            .filter(f -> sensitiveFields.stream().anyMatch(f.toLowerCase()::contains))
            .toList();
        if (!sensitiveRefs.isEmpty()) {
            violations.add("Sensitive field access detected: " + sensitiveRefs);
        }

        if (STAR_PATTERN.matcher(normalizedSql).find()) {
            violations.add("SELECT * is not allowed; specify explicit columns");
        }

        if (!LIMIT_PATTERN.matcher(normalizedSql).find()) {
            violations.add("Query must include a LIMIT clause");
        }

        if (CROSS_JOIN_PATTERN.matcher(normalizedSql).find()) {
            violations.add("CROSS JOIN is not allowed");
        }

        boolean isTimeSeries = referencedDatasets.stream().anyMatch(d ->
            d.contains("report") || d.contains("event") || d.contains("audit"));
        if (isTimeSeries && !TIME_RANGE_PATTERN.matcher(normalizedSql).find()) {
            violations.add("Time-series queries must include a time range filter");
        }

        String estimatedRisk = estimateRisk(violations);
        boolean requiresReview = violations.size() > 2 || "CRITICAL".equals(estimatedRisk);

        SqlSafetyResult result = new SqlSafetyResult(
            violations.isEmpty(), violations, normalizedSql,
            referencedDatasets, referencedFields, estimatedRisk, requiresReview
        );

        log.debug("SqlSafetyValidator: safe={}, violations={}", result.safe(), violations.size());
        return result;
    }

    private String normalize(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("\\s+", " ").trim();
    }

    private List<String> extractTableRefs(String sql) {
        List<String> refs = new ArrayList<>();
        Matcher m = TABLE_REF_PATTERN.matcher(sql);
        while (m.find()) {
            refs.add(m.group(2));
        }
        return refs;
    }

    private List<String> extractFieldRefs(String sql) {
        List<String> fields = new ArrayList<>();
        Matcher m = FIELD_REF_PATTERN.matcher(sql);
        if (m.find()) {
            String selectClause = m.group(1);
            for (String col : selectClause.split(",")) {
                String trimmed = col.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("*")) {
                    String fieldName = trimmed.replaceAll("\\s+AS\\s+\\w+", "").trim();
                    fieldName = fieldName.replaceAll("\\w+\\.", "");
                    fields.add(fieldName);
                }
            }
        }
        return fields;
    }

    private String estimateRisk(List<String> violations) {
        int score = 0;
        for (String v : violations) {
            if (v.contains("DDL") || v.contains("DML")) score += 40;
            else if (v.contains("semicolon")) score += 30;
            else if (v.contains("Sensitive")) score += 25;
            else if (v.contains("Unauthorized")) score += 20;
            else if (v.contains("CROSS JOIN")) score += 15;
            else if (v.contains("LIMIT")) score += 5;
            else score += 10;
        }
        if (score >= 60) return "CRITICAL";
        if (score >= 40) return "HIGH";
        if (score >= 20) return "MEDIUM";
        return "LOW";
    }
}
