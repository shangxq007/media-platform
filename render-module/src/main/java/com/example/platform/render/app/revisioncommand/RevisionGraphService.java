package com.example.platform.render.app.revisioncommand;

import com.example.platform.render.domain.revisioncommand.RevisionCommandErrorCode;
import com.example.platform.render.domain.revisioncommand.RevisionCommandException;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REVISION_COMMAND_MODEL_V1 (RC31/§25-26): narrow revision-graph mechanics over
 * timeline_revision_parent ONLY (single graph authority, RCI3). No generic
 * graph DB; no JGit. Owns ancestry traversal and best-common-ancestor
 * resolution. TimelineMergeEngine never discovers graph bases.
 */
public class RevisionGraphService {

    private final DSLContext dsl;

    public RevisionGraphService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** readParents(revisionId) -> ordered parents (parent_order asc) — graph authority only. */
    public List<String> readParents(String projectId, String revisionId) {
        return dsl.fetch(
                        "select parent_revision_id from timeline_revision_parent "
                                + "where project_id = ? and revision_id = ? order by parent_order",
                        projectId, revisionId)
                .map(r -> r.get(0, String.class));
    }

    /** isAncestor(ancestor, descendant): ancestor reachable via parent edges from descendant. */
    public boolean isAncestor(String projectId, String ancestor, String descendant) {
        Set<String> visited = new HashSet<>();
        List<String> frontier = new ArrayList<>();
        frontier.add(descendant);
        while (!frontier.isEmpty()) {
            String current = frontier.remove(frontier.size() - 1);
            if (current.equals(ancestor)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (String parent : readParents(projectId, current)) {
                frontier.add(parent);
            }
        }
        return false;
    }

    /**
     * findBestMergeBase(ours, theirs): unique best common ancestor (maximum
     * graph depth from the merge point); zero => NO_COMMON_ANCESTOR;
     * multiple equally-best => AMBIGUOUS_MERGE_BASE. No arbitrary sorting.
     */
    public String findBestMergeBase(String projectId, String ours, String theirs) {
        Set<String> ourAncestors = ancestors(projectId, ours);
        Set<String> theirAncestors = ancestors(projectId, theirs);
        ourAncestors.retainAll(theirAncestors);
        if (ourAncestors.isEmpty()) {
            throw new RevisionCommandException(RevisionCommandErrorCode.NO_COMMON_ANCESTOR,
                    "no common ancestor between " + ours + " and " + theirs);
        }
        // best = ancestors that have no descendant inside the common set (common set is
        // a lattice; multiple maximal elements => ambiguous)
        Set<String> maximal = new LinkedHashSet<>();
        for (String candidate : ourAncestors) {
            boolean hasDescendantInSet = false;
            for (String other : ourAncestors) {
                if (!candidate.equals(other) && isAncestor(projectId, candidate, other)) {
                    hasDescendantInSet = true;
                    break;
                }
            }
            if (!hasDescendantInSet) {
                maximal.add(candidate);
            }
        }
        if (maximal.size() > 1) {
            throw new RevisionCommandException(RevisionCommandErrorCode.AMBIGUOUS_MERGE_BASE,
                    "multiple equally-best merge bases: " + maximal);
        }
        return maximal.iterator().next();
    }

    private Set<String> ancestors(String projectId, String revisionId) {
        Set<String> result = new LinkedHashSet<>();
        result.add(revisionId);
        List<String> frontier = new ArrayList<>();
        frontier.add(revisionId);
        while (!frontier.isEmpty()) {
            String current = frontier.remove(frontier.size() - 1);
            for (String parent : readParents(projectId, current)) {
                if (result.add(parent)) {
                    frontier.add(parent);
                }
            }
        }
        return result;
    }
}
