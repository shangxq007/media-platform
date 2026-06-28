package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles a ProviderBindingPlan into a list of ProviderExecutionDocumentDrafts.
 *
 * <p>Each bound capability node produces a draft that records what kind of
 * execution document would be needed. Actual document generation is future work.</p>
 *
 * <p>v0 always returns {@code generationReady=false} — drafts are planning
 * artifacts only.</p>
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
@Service
public class ProviderExecutionDocumentDraftCompiler {

    private static final Logger log = LoggerFactory.getLogger(ProviderExecutionDocumentDraftCompiler.class);

    /**
     * Compile a binding plan into execution document drafts.
     *
     * @param bindingPlan the provider binding plan
     * @return list of drafts for all bound nodes
     */
    public List<ProviderExecutionDocumentDraft> compile(ProviderBindingPlan bindingPlan) {
        if (bindingPlan == null) {
            throw new IllegalArgumentException("ProviderBindingPlan must not be null");
        }

        List<ProviderExecutionDocumentDraft> drafts = new ArrayList<>();

        for (ProviderBindingNode node : bindingPlan.nodes()) {
            if (node.isBound() && node.decision().selectedProvider() != null) {
                ProviderExecutionDocumentDraft draft = createDraft(node, bindingPlan);
                drafts.add(draft);
            }
        }

        log.info("Execution document drafts compiled: planId={} drafts={}",
                bindingPlan.planId(), drafts.size());

        return List.copyOf(drafts);
    }

    /**
     * Create a draft for a single bound node.
     */
    private ProviderExecutionDocumentDraft createDraft(
            ProviderBindingNode node, ProviderBindingPlan bindingPlan) {

        String providerName = node.decision().selectedProvider().providerName();
        ProviderExecutionDocumentDraftType docType = resolveDocumentType(providerName);
        String draftId = computeDraftId(node.nodeId(), providerName, docType);

        return ProviderExecutionDocumentDraft.forNode(
                draftId, node.nodeId(), providerName, docType);
    }

    /**
     * Resolve the document type based on provider name.
     */
    private ProviderExecutionDocumentDraftType resolveDocumentType(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "ffmpeg" -> ProviderExecutionDocumentDraftType.FFMPEG_COMMAND_PLAN;
            case "mlt" -> ProviderExecutionDocumentDraftType.MLT_PROJECT_DOCUMENT;
            case "remotion" -> ProviderExecutionDocumentDraftType.REMOTION_INPUT_PROPS_DOCUMENT;
            case "blender" -> ProviderExecutionDocumentDraftType.BLENDER_SCENE_SPEC;
            case "natron" -> ProviderExecutionDocumentDraftType.NATRON_PROJECT_SPEC;
            case "gpac", "mp4box" -> ProviderExecutionDocumentDraftType.PACKAGING_PLAN;
            case "gstreamer" -> ProviderExecutionDocumentDraftType.GSTREAMER_PIPELINE_SPEC;
            case "openfx" -> ProviderExecutionDocumentDraftType.OPENFX_EFFECT_DESCRIPTOR;
            default -> ProviderExecutionDocumentDraftType.UNKNOWN;
        };
    }

    /**
     * Compute a deterministic draft ID.
     */
    private String computeDraftId(String nodeId, String providerName,
                                   ProviderExecutionDocumentDraftType docType) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(nodeId.getBytes(StandardCharsets.UTF_8));
            md.update(providerName.getBytes(StandardCharsets.UTF_8));
            md.update(docType.name().getBytes(StandardCharsets.UTF_8));
            return "edd-" + HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return "edd-" + nodeId;
        }
    }
}
