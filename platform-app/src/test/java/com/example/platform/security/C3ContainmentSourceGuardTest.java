package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class C3ContainmentSourceGuardTest {

    @Test
    void containedSourcesHaveZeroKnownExecutionAndDisclosurePatterns() {
        List<ForbiddenSourcePattern> forbidden = List.of(
                pattern("delivery-module/src/main/java/com/example/platform/delivery/api/DeliveryController.java",
                        "\"COMPLETED\""),
                pattern("platform-app/src/main/java/com/example/platform/web/render/TimelineGitV1Controller.java",
                        "request.createdBy()"),
                pattern("platform-app/src/main/java/com/example/platform/web/render/TimelineRevisionController.java",
                        "body.tenantId()"),
                pattern("platform-app/src/main/java/com/example/platform/web/render/TimelineReviewController.java",
                        "reviewService.approve("),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/ProductController.java",
                        "service.find("),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/AssetWorkbenchController.java",
                        "r.storageUri()"),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/AssetWorkbenchController.java",
                        "r.ownerId()"),
                pattern("render-module/src/main/java/com/example/platform/render/api/RenderController.java",
                        "orchestratorPort.submitRenderJob("),
                pattern("render-module/src/main/java/com/example/platform/render/api/RenderController.java",
                        "orchestratorPort.executeExistingRenderJob("),
                pattern("render-module/src/main/java/com/example/platform/render/api/RenderController.java",
                        "cacheCleanupService.runCleanup("),
                pattern("render-module/src/main/java/com/example/platform/render/api/RenderController.java",
                        "aiTimelineProposalService.adopt("),
                pattern("render-module/src/main/java/com/example/platform/render/api/RenderController.java",
                        "aiTimelineProposalService.reject("),
                pattern("render-module/src/main/java/com/example/platform/render/api/ClientExportController.java",
                        "session.outputUri()"),
                pattern("render-module/src/main/java/com/example/platform/render/app/clientexport/ClientExportService.java",
                        "ClientExportSession.STATUS_COMPLETED"),
                pattern("render-module/src/main/java/com/example/platform/render/app/clientexport/ClientExportService.java",
                        "Files.copy("),
                pattern("render-module/src/main/java/com/example/platform/render/app/asset/AssetEnrichmentService.java",
                        "EnrichmentStatus.COMPLETE"),
                pattern("render-module/src/main/java/com/example/platform/render/app/asset/AssetEnrichmentService.java",
                        "repository.update("),
                pattern("platform-app/src/main/java/com/example/platform/web/media/AssetIntegrityScanController.java",
                        "scanService.scanProject("),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/AssetPublishController.java",
                        "reviewService.submitForReview("),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/AssetPublishController.java",
                        "reviewService.publishAsset("),
                pattern("platform-app/src/main/java/com/example/platform/web/assets/MarketplaceController.java",
                        "listingRepo."));

        for (ForbiddenSourcePattern guard : forbidden) {
            assertEquals(0, occurrences(read(guard.path()), guard.token()),
                    guard.path() + " must have zero occurrences of " + guard.token());
        }
    }

    private static ForbiddenSourcePattern pattern(String path, String token) {
        return new ForbiddenSourcePattern(path, token);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String read(String relativePath) {
        try {
            return Files.readString(repoRoot().resolve(relativePath));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return candidate;
    }

    private record ForbiddenSourcePattern(String path, String token) {}
}
