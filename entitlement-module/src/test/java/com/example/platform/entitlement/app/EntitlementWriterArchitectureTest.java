package com.example.platform.entitlement.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EntitlementWriterArchitectureTest {
    private static final Path ROOT = repositoryRoot();

    @Test
    void onlySubordinateRepositoriesContainGrantMutationSql() throws IOException {
        List<Path> generic;
        List<Path> workspace;
        try (Stream<Path> files = Files.walk(ROOT)) {
            List<Path> production = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/")).toList();
            generic = production.stream().filter(path -> mutation(read(path), "entitlement_grant")).toList();
            workspace = production.stream()
                    .filter(path -> mutation(read(path), "workspace_member_entitlement_grant")).toList();
        }
        assertEquals(List.of(ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/EntitlementGrantRepository.java")), generic);
        assertEquals(List.of(ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/WorkspaceMemberEntitlementGrantRepository.java")), workspace);
    }

    @Test
    void entitlementServiceHasNoFailOpenGrantAuthorityAndCallersDoNotWriteRepositories() {
        String service = read(ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/app/EntitlementService.java"));
        assertFalse(service.contains("featureGrants"));
        assertFalse(service.contains("quotaProfiles"));
        assertFalse(service.contains("ConcurrentHashMap"));
        assertFalse(service.contains("pro_quota"));
        assertFalse(service.contains("render.job.create"));

        String controller = read(ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/api/EntitlementGrantController.java"));
        String pool = read(ROOT.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/app/WorkspaceEntitlementPoolService.java"));
        assertFalse(controller.contains("infrastructure.EntitlementGrantRepository"));
        assertFalse(pool.contains("WorkspaceMemberEntitlementGrantRepository"));
    }

    private static boolean mutation(String source, String table) {
        String lower = source.toLowerCase();
        return lower.contains("insert into " + table) || lower.contains("update " + table)
                || lower.contains("insertinto(" + table) || lower.contains("update(" + table);
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current;
        if (current.getParent() != null
                && Files.isRegularFile(current.getParent().resolve("settings.gradle.kts"))) return current.getParent();
        throw new IllegalStateException("Repository root not found");
    }
}
