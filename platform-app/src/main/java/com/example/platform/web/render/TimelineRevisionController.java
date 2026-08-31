package com.example.platform.web.render;

import com.example.platform.timeline.app.TimelinePatchOpsJson;
import com.example.platform.timeline.app.TimelineRevisionDiffService;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.render.app.timeline.RenderJobStatusService;
import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery.CompareResult;
import com.example.platform.timeline.app.TimelineRevisionQueryService.EditSessionInfo;
import com.example.platform.timeline.app.TimelineRevisionQueryService.RevisionDetail;
import com.example.platform.timeline.app.TimelineRevisionQueryService.RevisionInfo;
import com.example.platform.timeline.app.TimelineRevisionQueryService.RevisionSnapshotPayload;
import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.render.api.dto.TimelineRevisionRenderRequest;
import com.example.platform.render.api.dto.TimelineRevisionRenderResponse;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.events.TimelineMergedEvent;
import com.example.platform.shared.events.TimelineRestoredEvent;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.TimelineMergeSummary;
import com.example.platform.timeline.diff.merge.TimelineConflict;
import com.example.platform.timeline.diff.merge.TimelineResolutionIntent;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/render/projects/{projectId}/timeline/revisions")
@Tag(name = "Timeline Revisions", description = "Domain version control for project timelines")
public class TimelineRevisionController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService;
    private final com.example.platform.timeline.app.TimelineRevisionDiffQuery revisionDiffQuery;
    private final TimelineMergeEngine mergeEngine;
    private final TimelineReviewEventPublisher eventPublisher;
    private final TimelineRevisionRenderService renderService;
    private final RenderJobStatusService renderJobStatusService;
    private final com.example.platform.timeline.app.TimelineRevisionSaveService revisionSaveService;
    private final com.example.platform.timeline.app.TimelinePayloadCodec timelinePayloadCodec;
    private final TimelineProjectAuthorizationService projectAuthorization;

    public TimelineRevisionController(
            com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService,
            com.example.platform.timeline.app.TimelineRevisionDiffQuery revisionDiffQuery,
                                       TimelineMergeEngine mergeEngine,
                                       TimelineReviewEventPublisher eventPublisher,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) TimelineRevisionRenderService renderService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) RenderJobStatusService renderJobStatusService,
                                       com.example.platform.timeline.app.TimelineRevisionSaveService revisionSaveService,
                                       com.example.platform.timeline.app.TimelinePayloadCodec timelinePayloadCodec,
                                       TimelineProjectAuthorizationService projectAuthorization) {
        this.revisionQueryService = revisionQueryService;
        this.revisionDiffQuery = revisionDiffQuery;
        this.mergeEngine = mergeEngine;
        this.eventPublisher = eventPublisher;
        this.renderService = renderService;
        this.renderJobStatusService = renderJobStatusService;
        this.revisionSaveService = revisionSaveService;
        this.timelinePayloadCodec = timelinePayloadCodec;
        this.projectAuthorization = projectAuthorization;
    }

    @GetMapping
    @Operation(summary = "列出项目时间线修订历史")
    public List<RevisionListItem> list(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) String editSessionId,
            @RequestParam(required = false) String authorUserId,
            @RequestParam(required = false) String source) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        return revisionQueryService
                .listHistory(projectId, tenantId, editSessionId, authorUserId, source, limit)
                .stream()
                .map(TimelineRevisionController::toListItem)
                .toList();
    }

    @GetMapping("/facets")
    @Operation(summary = "项目修订筛选项（来源、作者）")
    public RevisionFacetsResponse facets(@PathVariable String projectId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        TimelineRevisionQueryService.RevisionFacets facets = revisionQueryService.listFacets(projectId, tenantId);
        return new RevisionFacetsResponse(
                facets.sources(),
                facets.authors().stream()
                        .map(a -> new AuthorFacetDto(a.authorUserId(), a.revisionCount()))
                        .toList());
    }

    @PatchMapping("/{revisionId}/annotation")
    @Operation(summary = "更新修订备注与标签（不生成新修订）")
    public ResponseEntity<RevisionListItem> updateAnnotation(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @RequestBody AnnotationRequest body) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireWrite(tenantId, projectId);
        return revisionQueryService
                .updateAnnotation(
                        projectId, tenantId,
                        revisionId,
                        body != null ? body.message() : null,
                        body != null ? body.labels() : null)
                .map(r -> ResponseEntity.ok(toListItem(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/edit-sessions")
    @Operation(summary = "列出项目的 AI 改稿会话分支")
    public List<EditSessionItem> editSessions(
            @PathVariable String projectId, @RequestParam(defaultValue = "20") int limit) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        return revisionQueryService.listEditSessions(projectId, tenantId, limit).stream()
                .map(s -> new EditSessionItem(s.editSessionId(), s.lastAt(), s.revisionCount()))
                .toList();
    }

    @GetMapping("/compare")
    @Operation(summary = "对比两个修订（实体级 diff）")
    public CompareResponse compare(
            @PathVariable String projectId,
            @RequestParam String from,
            @RequestParam String to) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        CompareResult result = revisionDiffQuery.compareRevisions(projectId, tenantId, from, to);
        return toCompareResponse(result);
    }

    @GetMapping("/head")
    @Operation(summary = "当前 HEAD 修订")
    public ResponseEntity<RevisionListItem> head(@PathVariable String projectId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        return revisionQueryService
                .findHead(projectId, tenantId)
                .map(r -> ResponseEntity.ok(toListItem(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/snapshot")
    @Operation(summary = "修订关联快照的 Internal Timeline JSON（供 patch 路径索引解析）")
    public ResponseEntity<RevisionSnapshotResponse> revisionSnapshot(
            @PathVariable String projectId, @PathVariable String revisionId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        return revisionQueryService
                .getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .map(p -> ResponseEntity.ok(new RevisionSnapshotResponse(
                        revisionId, p.snapshotId(), p.canonicalTimelineJson(), p.schemaVersion())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}")
    @Operation(summary = "修订详情（含变更摘要）")
    public ResponseEntity<RevisionDetailResponse> get(
            @PathVariable String projectId, @PathVariable String revisionId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, projectId);
        return revisionQueryService
                .getDetail(projectId, tenantId, revisionId)
                .map(d -> ResponseEntity.ok(toDetailResponse(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{revisionId}/restore")
    @Operation(summary = "回滚到指定修订（生成新 HEAD，不删除历史）")
    public ResponseEntity<RestoreResponse> restore(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @RequestParam(required = false) String authorUserId) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, projectId);
        // CFRH-I1: legacy restore authority (TimelineRevisionDiffQuery.restore) replaced by the
        // canonical restore transaction boundary (TimelineRevisionSaveService.restoreRevision).
        // expected-current CAS comes from the canonical current-revision authority.
        String expectedCurrent = revisionQueryService.findHead(projectId, tenantId)
                .map(com.example.platform.timeline.app.TimelineRevisionQueryService.RevisionInfo::id)
                .orElse(null);
        var restored = revisionSaveService.restoreRevision(
                tenantId, projectId, revisionId, expectedCurrent, actor.actorId());
        eventPublisher.publish(new TimelineRestoredEvent(projectId, revisionId, restored.revisionId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toRestoreResponse(projectId, restored.revisionId()));
    }

    @PostMapping("/merge")
    @Operation(summary = "三路合并（有冲突时返回冲突列表，不创建 revision）")
    public ResponseEntity<MergeApiResponse> merge(
            @PathVariable String projectId,
            @RequestBody MergeApiRequest body) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, projectId);
        TimelineMergeRequest request = new TimelineMergeRequest(
                projectId, tenantId,
                body.baseRevisionId(), body.sourceRevisionId(), body.targetRevisionId(),
                actor.actorId(), body.message());

        TimelineMergeResult result;
        if (body.resolutions() != null && !body.resolutions().isEmpty()) {
            Map<String, TimelineResolutionIntent> intents = new HashMap<>();
            for (var r : body.resolutions()) {
                var intent = switch (r.resolutionMode()) {
                    case "USE_SOURCE" ->
                        TimelineResolutionIntent.useSource(
                            new com.example.platform.timeline.diff.merge.EntityRef(
                                com.example.platform.timeline.diff.merge.EntityKind.CLIP,
                                r.entityId()), null);
                    case "USE_TARGET" ->
                        TimelineResolutionIntent.useTarget(
                            new com.example.platform.timeline.diff.merge.EntityRef(
                                com.example.platform.timeline.diff.merge.EntityKind.CLIP,
                                r.entityId()), null);
                    default -> null;
                };
                if (intent != null) {
                    intents.put(r.entityRef(), intent);
                }
            }
            result = mergeEngine.merge(request, intents);
        } else {
            result = mergeEngine.merge(request);
        }

        if (result.isMerged() && result.mergedRevisionId() != null) {
            eventPublisher.publish(new TimelineMergedEvent(projectId,
                    result.baseRevisionId(), result.sourceRevisionId(), result.targetRevisionId(),
                    result.mergedRevisionId(),
                    body.sourceRevisionId() + "," + body.targetRevisionId(),
                    result.baseRevisionId()));
        }

        return ResponseEntity.ok(toMergeResponse(result));
    }

    @PostMapping("/{revisionId}/render")
    @Operation(summary = "渲染 TimelineRevision 为最终视频 Product",
            description = "接受 TimelineRevision 引用和输出配置，使用 FFmpeg/libass 基线渲染路径生成最终视频。" +
                    "不暴露内部 provider/backend/environment 选择。")
    public ResponseEntity<TimelineRevisionRenderResponse> render(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @RequestBody TimelineRevisionRenderRequest request) {
        projectAuthorization.requireWrite(TenantContext.get(), projectId);
        if (renderService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(TimelineRevisionRenderResponse.failure(revisionId, "Timeline revision render service is not available"));
        }

        try {
            TimelineRevisionRenderService.RevisionRenderResult result =
                    renderService.render(projectId, revisionId, request.profileOrDefault());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(TimelineRevisionRenderResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(TimelineRevisionRenderResponse.failure(revisionId, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TimelineRevisionRenderResponse.failure(revisionId, e.getMessage()));
        }
    }

    @GetMapping("/{revisionId}/render-jobs/{renderJobId}")
    @Operation(summary = "查询渲染作业状态",
            description = "根据 renderJobId 查询渲染作业状态。" +
                    "返回 READY/FAILED/RUNNING 状态及输出 Product 引用。" +
                    "不暴露内部 provider/backend/environment 选择。")
    public ResponseEntity<RenderJobStatusResponse> getRenderJobStatus(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @PathVariable String renderJobId) {
        projectAuthorization.requireRead(TenantContext.get(), projectId);
        if (renderJobStatusService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return renderJobStatusService.findStatus(projectId, revisionId, renderJobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/render-jobs/{renderJobId}/result")
    @Operation(summary = "查询渲染作业结果",
            description = "根据 renderJobId 查询渲染作业的输出 Product 结果摘要。" +
                    "返回输出 Product 的安全元数据（格式、分辨率、时长等）。" +
                    "不暴露内部 provider/backend/environment 选择，不暴露 signed URL 或本地路径。")
    public ResponseEntity<RenderJobResultResponse> getRenderJobResult(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @PathVariable String renderJobId) {
        projectAuthorization.requireRead(TenantContext.get(), projectId);
        if (renderJobStatusService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return renderJobStatusService.findResult(projectId, revisionId, renderJobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static RevisionListItem toListItem(RevisionInfo r) {
        ChangeSummaryDto summary = parseChangeSummary(r.changeSummaryJson());
        return new RevisionListItem(
                r.id(),
                r.revisionNumber(),
                r.parentRevisionId(),
                r.snapshotId(),
                r.internalRevision(),
                r.source(),
                r.message(),
                r.labels() != null ? r.labels() : List.of(),
                r.authorUserId(),
                r.editSessionId(),
                TimelinePatchOpsJson.countOps(r.patchOpsJson()),
                r.createdAt(),
                summary,
                r.isMerge(),
                r.mergeParentRevisionIds(),
                r.mergeBaseRevisionId());
    }

    private static RevisionDetailResponse toDetailResponse(RevisionDetail d) {
        RevisionInfo r = d.revision();
        return new RevisionDetailResponse(
                toListItem(r),
                d.changeSummary() != null ? ChangeSummaryDto.from(d.changeSummary()) : ChangeSummaryDto.empty(),
                TimelinePatchOpsJson.countOps(r.patchOpsJson()));
    }

    private static CompareResponse toCompareResponse(CompareResult result) {
        List<PatchPathDto> paths = result.patchPaths().stream()
                .map(p -> new PatchPathDto(p.op(), p.path()))
                .toList();

        return new CompareResponse(
                toListItem(result.fromRevision()),
                toListItem(result.toRevision()),
                ChangeSummaryDto.from(result.summary()),
                result.entityChanges().stream()
                        .map(e -> new EntityChangeDto(e.kind(), e.entityId(), e.action()))
                        .toList(),
                paths,
                paths.size());
    }

    private RestoreResponse toRestoreResponse(String projectId, String newRevisionId) {
        // CFRH-I2: read the newly restored revision through the ownership-scoped
        // query authority (projectId + tenantId participate in the persistence read).
        String tenantId = TenantContext.get();
        var detail = revisionQueryService.getDetail(projectId, tenantId, newRevisionId);
        var info = detail.map(RevisionDetail::revision).orElse(null);
        var canonicalPayload = revisionQueryService.getRevisionSnapshotPayload(projectId, tenantId, newRevisionId)
                .map(RevisionSnapshotPayload::canonicalTimelineJson)
                .orElse(null);
        if (info == null || canonicalPayload == null) {
            // Canonical restore success guarantees the new revision + governed
            // payload exist; a missing read is an impossible post-restore state.
            throw new IllegalStateException(
                    "RESTORE_RESPONSE_INCOMPLETE: restored revision " + newRevisionId
                            + " missing detail or internal payload");
        }
        String editorPayload = timelinePayloadCodec.toEditorJson(canonicalPayload);
        return new RestoreResponse(
                toListItem(info),
                editorPayload,
                canonicalPayload);
    }

    private static ChangeSummaryDto parseChangeSummary(String json) {
        if (json == null || json.isBlank()) {
            return ChangeSummaryDto.empty();
        }
        try {
            return MAPPER.readValue(json, ChangeSummaryDto.class);
        } catch (Exception e) {
            return ChangeSummaryDto.empty();
        }
    }

    public record AnnotationRequest(String message, List<String> labels) {}

    public record RevisionFacetsResponse(List<String> sources, List<AuthorFacetDto> authors) {}

    public record AuthorFacetDto(String authorUserId, int revisionCount) {}

    public record EditSessionItem(String editSessionId, String lastAt, int revisionCount) {}

    public record RevisionListItem(
            String id,
            int revisionNumber,
            String parentRevisionId,
            String snapshotId,
            int internalRevision,
            String source,
            String message,
            List<String> labels,
            String authorUserId,
            String editSessionId,
            int patchOpCount,
            String createdAt,
            ChangeSummaryDto changeSummary,
            boolean isMerge,
            String mergeParentRevisionIds,
            String mergeBaseRevisionId) {}

    public record RevisionDetailResponse(
            RevisionListItem revision, ChangeSummaryDto changeSummary, int patchOpCount) {}

    public record RestoreResponse(
            RevisionListItem newRevision, String editorTimelineJson, String canonicalTimelineJson) {}

    public record CompareResponse(
            RevisionListItem fromRevision,
            RevisionListItem toRevision,
            ChangeSummaryDto summary,
            List<EntityChangeDto> entityChanges,
            List<PatchPathDto> patchPaths,
            int patchOpCount) {}

    public record RevisionSnapshotResponse(
            String revisionId, String snapshotId, String canonicalTimelineJson, String schemaVersion) {}

    public record PatchPathDto(String op, String path) {}

    public record EntityChangeDto(String kind, String entityId, String action) {}

    public record ChangeSummaryDto(
            boolean supported,
            int tracksAdded,
            int tracksRemoved,
            int tracksModified,
            int clipsAdded,
            int clipsRemoved,
            int clipsModified,
            int assetsAdded,
            int assetsRemoved,
            int parentInternalRevision,
            int currentInternalRevision) {

        static ChangeSummaryDto empty() {
            return new ChangeSummaryDto(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        static ChangeSummaryDto from(TimelineRevisionDiffService.ChangeSummary s) {
            return new ChangeSummaryDto(
                    s.supported(),
                    s.tracksAdded(),
                    s.tracksRemoved(),
                    s.tracksModified(),
                    s.clipsAdded(),
                    s.clipsRemoved(),
                    s.clipsModified(),
                    s.assetsAdded(),
                    s.assetsRemoved(),
                    s.parentInternalRevision(),
                    s.currentInternalRevision());
        }
    }

    public record MergeApiRequest(
            String baseRevisionId,
            String sourceRevisionId,
            String targetRevisionId,
            String message,
            List<ResolutionDto> resolutions) {}

    public record ResolutionDto(
            String conflictId,
            String entityRef,
            String entityId,
            String conflictType,
            String resolutionMode) {}

    public record MergeApiResponse(
            String status,
            String baseRevisionId,
            String sourceRevisionId,
            String targetRevisionId,
            String mergedRevisionId,
            List<MergeConflictDto> conflicts,
            MergeSummaryDto mergeSummary,
            String message) {}

    public record MergeConflictDto(
            String conflictId,
            String entityRef,
            String conflictType,
            String sourceChangeSummary,
            String targetChangeSummary,
            String message) {}

    public record MergeSummaryDto(
            int autoMergedCount,
            int conflictCount,
            int sourceChangesApplied,
            int targetChangesApplied,
            List<String> mergedEntityIds,
            List<String> conflictedEntityIds) {}

    private static MergeApiResponse toMergeResponse(TimelineMergeResult r) {
        List<MergeConflictDto> conflictDtos = r.conflicts() != null
                ? r.conflicts().stream()
                    .map(c -> new MergeConflictDto(
                            c.conflictId(),
                            c.entityRef().key(),
                            c.conflictType().name(),
                            c.sourceChange() != null ? c.sourceChange().summary() : "",
                            c.targetChange() != null ? c.targetChange().summary() : "",
                            c.message()))
                    .toList()
                : List.of();

        TimelineMergeSummary s = r.mergeSummary();
        MergeSummaryDto summary = s != null
                ? new MergeSummaryDto(
                    s.autoMergedCount(), s.conflictCount(),
                    s.sourceChangesApplied(), s.targetChangesApplied(),
                    s.mergedEntityIds(), s.conflictedEntityIds())
                : new MergeSummaryDto(0, 0, 0, 0, List.of(), List.of());

        return new MergeApiResponse(
                r.status().name(),
                r.baseRevisionId(), r.sourceRevisionId(), r.targetRevisionId(),
                r.mergedRevisionId(),
                conflictDtos, summary, r.summary());
    }

}
