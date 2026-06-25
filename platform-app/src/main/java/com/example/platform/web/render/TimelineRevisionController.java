package com.example.platform.web.render;

import com.example.platform.render.app.timeline.TimelinePatchOpsJson;
import com.example.platform.render.app.timeline.TimelineRevisionDiffService;
import com.example.platform.render.app.timeline.TimelineRevisionService;
import com.example.platform.render.app.timeline.TimelineRevisionService.CompareResult;
import com.example.platform.render.app.timeline.TimelineRevisionService.EditSessionInfo;
import com.example.platform.render.app.timeline.TimelineRevisionService.RevisionDetail;
import com.example.platform.render.app.timeline.TimelineRevisionService.RevisionInfo;
import com.example.platform.render.app.timeline.TimelineRevisionService.PatchPreviewResult;
import com.example.platform.render.app.timeline.TimelineRevisionService.PatchStepsResult;
import com.example.platform.render.app.timeline.TimelineRevisionService.RestoreResult;
import com.example.platform.render.app.timeline.TimelineRevisionService.RevisionSnapshotPayload;
import com.example.platform.render.app.timeline.TimelineMergeService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.events.TimelineMergedEvent;
import com.example.platform.shared.events.TimelineRestoredEvent;
import com.example.platform.render.domain.timeline.internal.TimelineMergeRequest;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult;
import com.example.platform.render.domain.timeline.internal.TimelineMergeSummary;
import com.example.platform.render.domain.timeline.internal.TimelineConflict;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent;
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
@RequestMapping("/api/v1/render/projects/{projectId}/timeline/revisions")
@Tag(name = "Timeline Revisions", description = "Domain version control for project timelines")
public class TimelineRevisionController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineRevisionService revisionService;
    private final TimelineMergeService mergeService;
    private final TimelineReviewEventPublisher eventPublisher;

    public TimelineRevisionController(TimelineRevisionService revisionService,
                                       TimelineMergeService mergeService,
                                       TimelineReviewEventPublisher eventPublisher) {
        this.revisionService = revisionService;
        this.mergeService = mergeService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    @Operation(summary = "列出项目时间线修订历史")
    public List<RevisionListItem> list(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) String editSessionId,
            @RequestParam(required = false) String authorUserId,
            @RequestParam(required = false) String source) {
        return revisionService
                .listHistory(projectId, editSessionId, authorUserId, source, limit)
                .stream()
                .map(TimelineRevisionController::toListItem)
                .toList();
    }

    @GetMapping("/facets")
    @Operation(summary = "项目修订筛选项（来源、作者）")
    public RevisionFacetsResponse facets(@PathVariable String projectId) {
        TimelineRevisionService.RevisionFacets facets = revisionService.listFacets(projectId);
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
        return revisionService
                .updateAnnotation(
                        projectId,
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
        return revisionService.listEditSessions(projectId, limit).stream()
                .map(s -> new EditSessionItem(s.editSessionId(), s.lastAt(), s.revisionCount()))
                .toList();
    }

    @GetMapping("/compare")
    @Operation(summary = "对比两个修订（实体级 diff）")
    public CompareResponse compare(
            @PathVariable String projectId,
            @RequestParam String from,
            @RequestParam String to) {
        CompareResult result = revisionService.compareRevisions(projectId, from, to);
        return toCompareResponse(result);
    }

    @GetMapping("/head")
    @Operation(summary = "当前 HEAD 修订")
    public ResponseEntity<RevisionListItem> head(@PathVariable String projectId) {
        return revisionService
                .findHead(projectId)
                .map(r -> ResponseEntity.ok(toListItem(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/patch-preview")
    @Operation(summary = "预览修订中存储的 RFC6902 patch（对父快照 dry-run）")
    public ResponseEntity<PatchPreviewResponse> patchPreview(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionService
                .findById(revisionId)
                .filter(r -> projectId.equals(r.projectId()))
                .map(r -> ResponseEntity.ok(toPatchPreview(revisionService.previewPatchReplay(revisionId))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/patch-steps")
    @Operation(summary = "分步预览 patch（每步单独 apply，累积 dry-run）")
    public ResponseEntity<PatchStepsResponse> patchSteps(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionService
                .findById(revisionId)
                .filter(r -> projectId.equals(r.projectId()))
                .map(r -> ResponseEntity.ok(toPatchSteps(revisionService.previewPatchSteps(revisionId))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}/snapshot")
    @Operation(summary = "修订关联快照的 Internal Timeline JSON（供 patch 路径索引解析）")
    public ResponseEntity<RevisionSnapshotResponse> revisionSnapshot(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionService
                .getRevisionSnapshotPayload(projectId, revisionId)
                .map(p -> ResponseEntity.ok(new RevisionSnapshotResponse(
                        revisionId, p.snapshotId(), p.internalTimelineJson(), p.schemaVersion())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{revisionId}")
    @Operation(summary = "修订详情（含变更摘要）")
    public ResponseEntity<RevisionDetailResponse> get(
            @PathVariable String projectId, @PathVariable String revisionId) {
        return revisionService
                .getDetail(revisionId)
                .filter(d -> projectId.equals(d.revision().projectId()))
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
        RestoreResult result = revisionService.restore(projectId, tenantId, revisionId, authorUserId);
        eventPublisher.publish(new TimelineRestoredEvent(projectId, revisionId, result.newRevision().id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toRestoreResponse(result));
    }

    @PostMapping("/merge")
    @Operation(summary = "三路合并（有冲突时返回冲突列表，不创建 revision）")
    public ResponseEntity<MergeApiResponse> merge(
            @PathVariable String projectId,
            @RequestBody MergeApiRequest body) {
        String tenantId = body.tenantId() != null ? body.tenantId() : TenantContext.get();
        TimelineMergeRequest request = new TimelineMergeRequest(
                projectId, tenantId,
                body.baseRevisionId(), body.sourceRevisionId(), body.targetRevisionId(),
                body.authorUserId(), body.message());

        TimelineMergeResult result;
        if (body.resolutions() != null && !body.resolutions().isEmpty()) {
            Map<String, TimelineResolutionIntent> intents = new HashMap<>();
            for (var r : body.resolutions()) {
                var intent = switch (r.resolutionMode()) {
                    case "USE_SOURCE" ->
                        TimelineResolutionIntent.useSource(
                            new com.example.platform.render.domain.timeline.internal.EntityRef(
                                com.example.platform.render.domain.timeline.internal.EntityKind.CLIP,
                                r.entityId()), null);
                    case "USE_TARGET" ->
                        TimelineResolutionIntent.useTarget(
                            new com.example.platform.render.domain.timeline.internal.EntityRef(
                                com.example.platform.render.domain.timeline.internal.EntityKind.CLIP,
                                r.entityId()), null);
                    default -> null;
                };
                if (intent != null) {
                    intents.put(r.entityRef(), intent);
                }
            }
            result = mergeService.threeWayMergeWithResolutions(request, intents);
        } else {
            result = mergeService.threeWayMerge(request);
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

    private static RestoreResponse toRestoreResponse(RestoreResult result) {
        return new RestoreResponse(
                toListItem(result.newRevision()),
                result.editorTimelineJson(),
                result.internalTimelineJson());
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
            int patchOpCount) {}

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
