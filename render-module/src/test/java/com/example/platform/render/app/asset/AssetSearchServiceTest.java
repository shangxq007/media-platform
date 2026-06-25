package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.asset.search.*;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetSearchServiceTest {

    private AssetRepository assetRepository;
    private AssetSemanticMetadataRepository semanticRepo;
    private AssetSearchService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        semanticRepo = mock(AssetSemanticMetadataRepository.class);
        SearchProjectionRepository projRepo = mock(SearchProjectionRepository.class);
        service = new AssetSearchService(assetRepository, semanticRepo, projRepo);
    }

    private Asset asset(String id, String type, String filename, String classif, boolean aiGen) {
        return new Asset(id, "tenant_1", "proj_1", "s3://bucket/" + id, type, filename,
                100L, "sha256:" + id, null, null, null, "v1", null, null,
                classif, null, null, null, false, aiGen, "DRAFT", Instant.now(), Instant.now());
    }

    @Test
    void shouldSearchByFilename() {
        when(assetRepository.listByProject("tenant_1", "proj_1"))
                .thenReturn(List.of(asset("a1", "VIDEO", "openai_interview.mp4", null, false)));

        var response = service.search(AssetSearchRequest.of("openai"), "tenant_1", "proj_1");

        assertEquals(1, response.total());
        assertEquals("a1", response.results().get(0).assetId());
        assertTrue(response.results().get(0).score() > 0);
    }

    @Test
    void shouldSearchByTranscript() {
        when(assetRepository.listByProject("tenant_1", "proj_1"))
                .thenReturn(List.of(asset("a2", "VIDEO", "vid.mp4", null, false)));

        String semanticJson = "{\"transcripts\":[{\"text\":\"We discuss OpenAI collaboration\"}]}";
        var row = new AssetSemanticMetadataRepository.SemanticRow(
                "a2", "v1", "COMPLETE", "en", semanticJson, null, null);
        when(semanticRepo.findById("a2")).thenReturn(Optional.of(row));

        var response = service.search(AssetSearchRequest.of("openai"), "tenant_1", "proj_1");

        assertEquals(1, response.total());
        assertTrue(response.results().get(0).score() >= 10);
    }

    @Test
    void shouldFilterByAssetType() {
        when(assetRepository.listByProject("tenant_1", "proj_1"))
                .thenReturn(List.of(
                        asset("a1", "VIDEO", "v.mp4", null, false),
                        asset("a2", "IMAGE", "i.png", null, false)));

        var req = new AssetSearchRequest(null, List.of("IMAGE"), null, null, null, null, 1, 20, "score");
        var response = service.search(req, "tenant_1", "proj_1");

        assertEquals(1, response.total());
        assertEquals("IMAGE", response.results().get(0).assetType());
    }

    @Test
    void shouldReturnEmptyForNoMatches() {
        when(assetRepository.listByProject("tenant_1", "proj_1"))
                .thenReturn(List.of(asset("a1", "VIDEO", "v.mp4", null, false)));

        var response = service.search(AssetSearchRequest.of("nonexistent"), "tenant_1", "proj_1");

        assertEquals(0, response.total());
    }

    @Test
    void shouldPaginateResults() {
        List<Asset> assets = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            assets.add(asset("a" + i, "VIDEO", "search_result_" + i + ".mp4", null, false));
        }
        when(assetRepository.listByProject("tenant_1", "proj_1")).thenReturn(assets);

        var response = service.search(AssetSearchRequest.of("search_result", 2, 10), "tenant_1", "proj_1");

        assertEquals(25, response.total());
        assertEquals(10, response.results().size());
        assertEquals(2, response.page());
    }
}
