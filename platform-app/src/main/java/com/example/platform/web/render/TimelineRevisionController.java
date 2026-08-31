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
import com.example.platform.timeline.app.TimelineRevisionDiffQuery.PatchPreviewResult;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery.PatchStepsResult;
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
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
    private final com.example.platform.timeline.app.ProductCurrentRevisionService currentRevisionService;
    private final com.example.platform.timeline.app.TimelinePayloadCodec timelinePayloadCodec;

    public TimelineRevisionController(
            com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService,
            com.example.platform.timeline.app.TimelineRevisionDiffQuery revisionDiffQuery,
                                       TimelineMergeEngine mergeEngine,
                                       TimelineReviewEventPublisher eventPublisher,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) TimelineRevisionRenderService renderService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) RenderJobStatusService renderJobStatusService,
                                       com.example.platform.timeline.app.TimelineRevisionSaveService revisionSaveService,
                                       com.example.platform.timeline.app.ProductCurrentRevisionService currentRevisionService,
                                       com.example.platform.timeline.app.TimelinePayloadCodec timelinePayloadCodec) {
        this.revisionQueryService = revisionQueryService;
        this.revisionDiffQuery = revisionDiffQuery;
        this.mergeEngine = mergeEngine;
        this.eventPublisher = eventPublisher;
        this.renderService = renderService;
        this.renderJobStatusService = renderJobStatusService;
        this.revisionSaveService = revisionSaveService;
        this.currentRevisionService = currentRevisionService;
        this.timelinePayloadCodec = timelinePayloadCodec;
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
        return revisionQueryService
                .listHistory(projectId, tenantId, editSessionId, authorUserId, source, limit)
                .stream()
                .map(TimelineRevisionController::toListItem)
                .toList();
    }

    @GetMapping("/facets")
    @Operation(summary = "项目修订筛选项（来源、作者）")
    public RevisionFacetsResponse facets(@PathVariable String projectId) {
        TimelineRevisionQueryService.RevisionFacets facets = revisionQueryService.listFacets(projectId, TenantContext.get());
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
        throw FailClosedAuthorization.unavailable("timeline revision annotation mutation");
    }

    @GetMapping("/edit-sessions")
    @Operation(summary = "列出项目的 AI 改稿会话分支")
    public List<EditSessionItem> editSessions(
            @PathVariable String projectId, @RequestParam(defaultValue = "20") int limit) {
        return revisionQueryService.listEditSessions(projectId, TenantContext.get(), limit).stream()
                .map(s -> new EditSessionItem(s.editSessionId(), s.lastAt(), s.revisionCount()))
                .toList();
    }

    @GetMapping("/compare")
    @Operation(summary = "对比两个修订（实体级 diff）")
    public CompareResponse compare(
            @PathVariable String projectId,
            @RequestParam String from,
            @RequestParam String to) {
        CompareResult result = revisionDiffQuery.compareRevisions(projectId, TenantContext.get(), from, to);
        return toCompareResponse(result);
    }

    @GetMapping("/head")
    @Operation(summary = "当前 HEAD 修订")
    public ResponseEntity<RevisionListItem> head(@PathVariable String projectId) {
        return revisionQueryService
                .findHead(projectId, TenantContext.get())
                .map(r -> ResponseEntity.ok(toListItem(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/patch-preview")
    @Operation(summary = "预览修订中存储的 RFC6902 patch（对父快照 dry-run）")
    public ResponseEntity<PatchPreviewResponse> patchPreview(
            @PathVariable String projectId, @PathVariable String revisionId) {
        String tenantId = TenantContext.get();
        return revisionQueryService
                .findById(projectId, tenantId, revisionId)
                .map(r -> ResponseEntity.ok(toPatchPreview(revisionDiffQuery.previewPatchReplay(projectId, tenantId, revisionId))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/patch-steps")
    @Operation(summary = "分步预览 patch（每步单独 apply，累积 dry-run）")
    public ResponseEntity<PatchStepsResponse> patchSteps(
            @PathVariable String projectId, @PathVariable String revisionId) {
        String tenantId = TenantContext.get();
        return revisionQueryService
                .findById(projectId, tenantId, revisionId)
                .map(r -> ResponseEntity.ok(toPatchSteps(revisionDiffQuery.previewPatchSteps(projectId, tenantId, revisionId))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/snapshot")
    @Operation(summary = "修订关联快照的 Internal Timeline JSON（供 patch 路径索引解析）")
    public ResponseEntity<RevisionSnapshotResponse> revisionSnapshot(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionQueryService
                .getRevisionSnapshotPayload(projectId, TenantContext.get(), revisionId)
                .map(p -> ResponseEntity.ok(new RevisionSnapshotResponse(
                        revisionId, p.snapshotId(), p.internalTimelineJson(), p.schemaVersion())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}")
    @Operation(summary = "修订详情（含变更摘要）")
    public ResponseEntity<RevisionDetailResponse> get(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionQueryService
                .getDetail(projectId, TenantContext.get(), revisionId)
                .map(d -> ResponseEntity.ok(toDetailResponse(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{revisionId}/restore")
    @Operation(summary = "回滚到指定修订（生成新 HEAD，不删除历史）")
    public ResponseEntity<RestoreResponse> restore(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @RequestParam(required = false) String authorUserId) {
        throw FailClosedAuthorization.unavailable("timeline revision restore");
    }

    @PostMapping("/merge")
    @Operation(summary = "三路合并（有冲突时返回冲突列表，不创建 revision）")
    public ResponseEntity<MergeApiResponse> merge(
            @PathVariable String projectId,
            @RequestBody MergeApiRequest body) {
        throw FailClosedAuthorization.unavailable("timeline revision merge");
    }

    @PostMapping("/{revisionId}/render")
    @Operation(summary = "渲染 TimelineRevision 为最终视频 Product",
            description = "接受 TimelineRevision 引用和输出配置，使用 FFmpeg/libass 基线渲染路径生成最终视频。" +
                    "不暴露内部 provider/backend/environment 选择。")
    public ResponseEntity<TimelineRevisionRenderResponse> render(
            @PathVariable String projectId,
            @PathVariable String revisionId,
            @RequestBody TimelineRevisionRenderRequest request) {
        throw FailClosedAuthorization.unavailable("timeline revision render creation");
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

        // Map semantic diff
        SemanticDiffDto semanticDto = null;
        if (result.semanticDiff() != null) {
            var sd = result.semanticDiff();
            List<SemanticChangeDto> changeDtos = sd.changes().stream()
                    .map(c -> new SemanticChangeDto(
                            c.type().name(),
                            c.entity().kind().name(),
                            c.entity().id(),
                            c.summary(),
                            isRenderAffecting(c.type().name())))
                    .toList();
            semanticDto = new SemanticDiffDto(true, sd.structurallyEqual(), changeDtos.size(), changeDtos);
        }

        return new CompareResponse(
                toListItem(result.fromRevision()),
                toListItem(result.toRevision()),
                ChangeSummaryDto.from(result.summary()),
                result.entityChanges().stream()
                        .map(e -> new EntityChangeDto(e.kind(), e.entityId(), e.action()))
                        .toList(),
                paths,
                paths.size(),
                semanticDto);
    }

    private static boolean isRenderAffecting(String changeType) {
        // All semantic changes are render-affecting except metadata-only
        return !changeType.contains("METADATA") && !changeType.contains("MESSAGE") && !changeType.contains("AUTHOR");
    }

    private static PatchPreviewResponse toPatchPreview(PatchPreviewResult r) {
        return new PatchPreviewResponse(
                r.revisionId(),
                r.hasPatchOps(),
                r.success(),
                r.patchPaths().stream().map(p -> new PatchPathDto(p.op(), p.path())).toList(),
                r.appliedOps(),
                r.errors(),
                r.contentHashBefore(),
                r.contentHashAfter(),
                r.revisionContentHash());
    }

    private static PatchStepsResponse toPatchSteps(PatchStepsResult r) {
        return new PatchStepsResponse(
                r.revisionId(),
                r.hasPatchOps(),
                r.allStepsSucceeded(),
                r.steps().stream()
                        .map(s -> new PatchStepDto(
                                s.stepIndex(),
                                s.op(),
                                s.path(),
                                s.success(),
                                s.appliedOps(),
                                s.errors(),
                                s.contentHashAfter()))
                        .toList());
    }

    private RestoreResponse toRestoreResponse(String projectId, String newRevisionId) {
        // CFRH-I2: read the newly restored revision through the ownership-scoped
        // query authority (projectId + tenantId participate in the persistence read).
        String tenantId = TenantContext.get();
        var detail = revisionQueryService.getDetail(projectId, tenantId, newRevisionId);
        var info = detail.map(RevisionDetail::revision).orElse(null);
        var internalPayload = revisionQueryService.getRevisionSnapshotPayload(projectId, tenantId, newRevisionId)
                .map(RevisionSnapshotPayload::internalTimelineJson)
                .orElse(null);
        if (info == null || internalPayload == null) {
            // Canonical restore success guarantees the new revision + governed
            // payload exist; a missing read is an impossible post-restore state.
            throw new IllegalStateException(
                    "RESTORE_RESPONSE_INCOMPLETE: restored revision " + newRevisionId
                            + " missing detail or internal payload");
        }
        String editorPayload = timelinePayloadCodec.toEditorJson(internalPayload);
        return new RestoreResponse(
                toListItem(info),
                editorPayload,
                internalPayload);
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
            RevisionListItem newRevision, String editorTimelineJson, String internalTimelineJson) {}

    public record CompareResponse(
            RevisionListItem fromRevision,
            RevisionListItem toRevision,
            ChangeSummaryDto summary,
            List<EntityChangeDto> entityChanges,
            List<PatchPathDto> patchPaths,
            int patchOpCount,
            SemanticDiffDto semanticDiff) {}

    public record SemanticDiffDto(
            boolean supported,
            boolean structurallyEqual,
            int changeCount,
            List<SemanticChangeDto> changes) {}

    public record SemanticChangeDto(
            String changeType,
            String entityKind,
            String entityId,
            String description,
            boolean renderAffecting) {}

    public record PatchPreviewResponse(
            String revisionId,
            boolean hasPatchOps,
            boolean success,
            List<PatchPathDto> patchPaths,
            List<String> appliedOps,
            List<String> errors,
            String contentHashBefore,
            String contentHashAfter,
            String revisionContentHash) {}

    public record PatchStepsResponse(
            String revisionId,
            boolean hasPatchOps,
            boolean allStepsSucceeded,
            List<PatchStepDto> steps) {}

    public record PatchStepDto(
            int stepIndex,
            String op,
            String path,
            boolean success,
            List<String> appliedOps,
            List<String> errors,
            String contentHashAfter) {}

    public record RevisionSnapshotResponse(
            String revisionId, String snapshotId, String internalTimelineJson, String schemaVersion) {}

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
            String tenantId,
            String baseRevisionId,
            String sourceRevisionId,
            String targetRevisionId,
            String authorUserId,
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
