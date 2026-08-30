package com.example.platform.billing.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class I5PricingRatingArchitectureTest {

    private static final Path ROOT = locateRoot();
    private static final Pattern FLOAT_TYPE = Pattern.compile("\\b(?:double|Double|float|Float)\\b");

    @Test
    void pricingRatingAuthorityHasNoProcessMapOrFloatingCommercialTypes() throws IOException {
        List<Path> roots = List.of(
                ROOT.resolve("billing-module/src/main/java/com/example/platform/billing/app"),
                ROOT.resolve("billing-module/src/main/java/com/example/platform/billing/domain"),
                ROOT.resolve("billing-module/src/main/java/com/example/platform/billing/api/dto"),
                ROOT.resolve("billing-module/src/main/java/com/example/platform/billing/infrastructure"));
        List<String> commercialNames = List.of("Pricing", "Price", "Discount", "Rating", "RatedUsage");
        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    if (commercialNames.stream().noneMatch(name -> path.getFileName().toString().contains(name))) continue;
                    String source = Files.readString(path);
                    assertFalse(FLOAT_TYPE.matcher(source).find(), () -> "floating commercial type: " + path);
                    assertFalse(source.contains("ConcurrentHashMap"), () -> "process pricing/rating map: " + path);
                    assertFalse(source.contains("Math.round"), () -> "manual floating rounding: " + path);
                }
            }
        }
    }

    @Test
    void schemaUsesVersionedExactPricingAndDurableRatedUsage() throws IOException {
        String schema = Files.readString(ROOT.resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        assertTrue(schema.contains("rule_version bigint not null"));
        assertTrue(schema.contains("discount_numerator bigint"));
        assertTrue(schema.contains("discount_denominator bigint"));
        assertTrue(schema.contains("payload_fingerprint varchar(64) not null"));
        assertTrue(schema.contains("unique (tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version)"));
        assertFalse(pricingTableBlock(schema, "custom_pricing_rule").contains("double precision"));
        String discount = pricingTableBlock(schema, "discount_policy");
        assertFalse(discount.contains("double precision"));
        assertTrue(discount.contains("tenant_id varchar(64) not null"));
        assertTrue(discount.contains("meter_key varchar(128) not null"));
        assertTrue(discount.contains("rule_version bigint not null"));
        assertTrue(discount.contains("currency_code varchar(3) not null"));
    }

    @Test
    void eachPricingAndRatingTableHasOneSqlWriter() throws IOException {
        assertEquals(1, writerFiles("pricing_rule"));
        assertEquals(1, writerFiles("custom_pricing_rule"));
        assertEquals(1, writerFiles("discount_policy"));
        assertEquals(1, writerFiles("rated_usage_record"));
    }

    @Test
    void pricingApiCarriesStableRuleVersionAndDoesNotDefaultUnknownModels() throws IOException {
        String create = Files.readString(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/api/dto/CreatePricingRuleRequest.java"));
        String response = Files.readString(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/api/dto/PricingRuleResponse.java"));
        String controller = Files.readString(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/api/PricingRuleController.java"));
        assertTrue(create.contains("long ruleVersion"));
        assertTrue(response.contains("long ruleVersion"));
        assertFalse(controller.contains("model = PricingModel.USAGE_BASED"));
    }

    private static long writerFiles(String table) throws IOException {
        Path production = ROOT.resolve("billing-module/src/main/java");
        try (var paths = Files.walk(production)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path).toLowerCase();
                            return source.contains("insert into " + table)
                                    || source.contains("update " + table)
                                    || source.contains("delete from " + table);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).count();
        }
    }

    private static String pricingTableBlock(String schema, String table) {
        int start = schema.indexOf("create table " + table);
        int end = schema.indexOf("create table ", start + 1);
        return schema.substring(start, end < 0 ? schema.length() : end);
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
