package com.example.platform.render.app.revisioncommand;

import com.example.platform.render.domain.revisioncommand.RevisionCommandErrorCode;
import com.example.platform.render.domain.revisioncommand.RevisionCommandException;
import com.example.platform.render.domain.revisioncommand.RevisionCommandPlan;
import com.example.platform.render.domain.revisioncommand.RevisionCommandPlanDigest;
import com.example.platform.render.domain.revisioncommand.RevisionRef;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.render.app.timeline.TimelineMergeEngine;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import org.jooq.DSLContext;

/**
 * REVISION_COMMAND_MODEL_V1 (RC8/§18/§22/§26-29): planner producing immutable
 * RevisionCommandPlan variants from exact resolved inputs. Resolves mutable
 * refs to exact revisions BEFORE plan freeze; never reads mutable latest at
 * apply. Merge delegates semantic computation to TimelineMergeEngine (pure).
 */
public class RevisionCommandPlanner {

    private final DSLContext dsl;
    private final RevisionGraphService graph;
    private final TimelineMergeEngine mergeEngine;
    private final TimelineContentDigester digester = new TimelineContentDigester();

    public RevisionCommandPlanner(DSLContext dsl, RevisionGraphService graph,
                                  TimelineMergeEngine mergeEngine) {
        this.dsl = dsl;
        this.graph = graph;
        this.mergeEngine = mergeEngine;
    }

    public RevisionCommandPlan planCreateRef(String projectId, String refId, String sourceRevisionId) {
        assertRevision(projectId, sourceRevisionId);
        String digest = RevisionCommandPlanDigest.createRef(projectId, refId, sourceRevisionId);
        return new RevisionCommandPlan.CreateRefPlan(projectId, new RevisionRef(projectId, refId),
                sourceRevisionId, digest);
    }

    public RevisionCommandPlan planDeleteRef(String projectId, String refId, String expectedHead) {
        String digest = RevisionCommandPlanDigest.deleteRef(projectId, refId, expectedHead);
        return new RevisionCommandPlan.DeleteRefPlan(projectId, new RevisionRef(projectId, refId),
                expectedHead, digest);
    }

    public RevisionCommandPlan planRestore(String projectId, String historicalSourceRevisionId,
                                           String targetRefId, String expectedTargetHead) {
        assertRevision(projectId, historicalSourceRevisionId);
        TimelineDocument historical = loadRevisionTimeline(projectId, historicalSourceRevisionId);
        String candidateHash = digester.digest(historical);
        String digest = RevisionCommandPlanDigest.restore(projectId, historicalSourceRevisionId,
                targetRefId, expectedTargetHead, candidateHash);
        return new RevisionCommandPlan.RestoreRevisionPlan(projectId, historicalSourceRevisionId,
                new RevisionRef(projectId, targetRefId), expectedTargetHead, candidateHash, digest);
    }

    public RevisionCommandPlan planMerge(String projectId, String sourceRevisionId,
                                         String targetRefId, String targetOursRevisionId) {
        assertRevision(projectId, sourceRevisionId);
        assertRevision(projectId, targetOursRevisionId);
        // RCP1 CASE A: exact same frozen revision merge => semantic NO_OP (candidate
        // hash == target hash drives the apply NO_OP path; expected head still checked
        // on first execution). CASE B (same ref, inconsistent frozen revisions) is a
        // resolution-layer malformed request, not this equality.
        if (sourceRevisionId.equals(targetOursRevisionId)) {
            TimelineDocument ours = loadRevisionTimeline(projectId, targetOursRevisionId);
            String sameHash = digester.digest(ours);
            String digest = RevisionCommandPlanDigest.merge(projectId, sourceRevisionId, targetRefId,
                    targetOursRevisionId, targetOursRevisionId, sameHash, false);
            return new RevisionCommandPlan.MergeRevisionPlan(projectId, sourceRevisionId,
                    new RevisionRef(projectId, targetRefId), targetOursRevisionId, targetOursRevisionId,
                    sameHash, false, "{}", digest);
        }
        String mergeBase = graph.findBestMergeBase(projectId, targetOursRevisionId, sourceRevisionId);
        // RCI5: pure semantic engine path (zero persistence)
        TimelineMergeResult result = mergeEngine.mergeSemantic(new TimelineMergeRequest(
                projectId, null, mergeBase, sourceRevisionId, targetOursRevisionId, null, "merge"));
        boolean conflict = result.status() == TimelineMergeResult.MergeStatus.CONFLICTS;
        TimelineDocument ours = loadRevisionTimeline(projectId, targetOursRevisionId);
        String candidateHash = conflict ? digester.digest(ours)
                : digester.digest(loadMergedTimeline(result));
        String mergedPayloadJson = result.mergedPayloadJson();
        String digest = RevisionCommandPlanDigest.merge(projectId, sourceRevisionId, targetRefId,
                targetOursRevisionId, mergeBase, candidateHash, conflict);
        return new RevisionCommandPlan.MergeRevisionPlan(projectId, sourceRevisionId,
                new RevisionRef(projectId, targetRefId), targetOursRevisionId, mergeBase,
                candidateHash, conflict, mergedPayloadJson, digest);
    }

    private TimelineDocument loadMergedTimeline(TimelineMergeResult result) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    result.mergedPayloadJson(), TimelineDocument.class);
        } catch (Exception e) {
            throw new RevisionCommandException(RevisionCommandErrorCode.REVISION_GRAPH_FAILURE,
                    "cannot materialize merged candidate");
        }
    }

    private TimelineDocument loadRevisionTimeline(String projectId, String revisionId) {
        String payload = dsl.fetchOne("select snapshot_id from timeline_revision "
                        + "where id = ? and project_id = ?", revisionId, projectId)
                .get(0, String.class);
        // bounded: snapshot payload stored by snapshot service; use JSON snapshot table lookup
        var snap = dsl.fetchOne("select payload from timeline_snapshot where id = ?", payload);
        if (snap == null) {
            throw new RevisionCommandException(RevisionCommandErrorCode.UNSUPPORTED_HISTORY_FORMAT,
                    "snapshot payload not materializable for " + revisionId);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    snap.get(0, String.class), TimelineDocument.class);
        } catch (Exception e) {
            throw new RevisionCommandException(RevisionCommandErrorCode.UNSUPPORTED_HISTORY_FORMAT,
                    "historical payload not readable: " + revisionId);
        }
    }

    private void assertRevision(String projectId, String revisionId) {
        Integer count = dsl.fetchOne("select count(*) from timeline_revision where id = ? and project_id = ?",
                revisionId, projectId).get(0, Integer.class);
        if (count == null || count == 0) {
            throw new RevisionCommandException(RevisionCommandErrorCode.REVISION_NOT_FOUND,
                    "revision not found in project: " + revisionId);
        }
    }
}
