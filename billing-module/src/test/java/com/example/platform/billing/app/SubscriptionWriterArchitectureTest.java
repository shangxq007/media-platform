package com.example.platform.billing.app;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SubscriptionWriterArchitectureTest {

    private static final Path ROOT = repositoryRoot();

    @Test
    void subscriptionJdbcRepositoryIsTheOnlyProductionContractSqlWriter() throws IOException {
        List<Path> writers;
        try (Stream<Path> files = Files.walk(ROOT)) {
            writers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/"))
                    .filter(path -> {
                        String source = read(path).toLowerCase();
                        return source.contains("insert into subscription_contract")
                                || source.contains("update subscription_contract")
                                || source.contains("insertinto(subscription_contract)")
                                || source.contains("update(subscription_contract)");
                    }).toList();
        }
        assertEquals(List.of(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionJdbcRepository.java")),
                writers);
    }

    @Test
    void projectionExposesNoMutationMethodsOrRepositoryWrites() {
        List<String> methodNames = Arrays.stream(BillingProjectionService.class.getDeclaredMethods())
                .map(Method::getName).toList();
        assertFalse(methodNames.contains("activateSubscription"));
        assertFalse(methodNames.contains("updateInvoice"));
        assertFalse(methodNames.contains("createContract"));
        String source = read(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/app/BillingProjectionService.java"));
        assertFalse(source.contains(".save("));
        assertFalse(source.contains("tenantOrDefault"));
        assertFalse(source.contains("\"tenant\""));
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current;
        if (current.getParent() != null
                && Files.isRegularFile(current.getParent().resolve("settings.gradle.kts"))) {
            return current.getParent();
        }
        throw new IllegalStateException("Repository root not found from " + current);
    }
}
