# CFRH-I1 — AI-ADOPT VALUE-FLOW LOSSLESSNESS EVIDENCE

Evidence for the recordAiAdoptRevision disposition decision. This file is the
authoritative value-flow proof; it is NOT a keyword-grep conclusion.

## Question

CAN_AI_PATH_AUTHOR_TRANSITIONS / CAN_AI_PATH_AUTHOR_TIMELINE_AUTOMATION /
CAN_AI_PATH_AUTHOR_EFFECT_AUTOMATION

## Value flow (exact symbols)

1. RenderController.java L302: `aiTimelineProposalService.adopt(request.timelineJson(), proposalId)`
   - Input `request.timelineJson()` = AiProposalResolveRequest.timelineJson,
     client-supplied current timeline JSON (contains platformExtensions.aiProposals).
2. RenderController.java L307: `timelineConversionService.ensureInternalTimelineJson(resolved.timelineJson())`
   - Normalizes to internal-1.0 schema; does NOT filter semantic fields.
3. RenderController.java L308-314: `timelineRevisionService.recordAiAdoptRevision(projectId, tenantId, internal, request.editSessionId(), proposalId, resolved.patchOperations())`
4. AiTimelineEditService.java L49-58: `editTimeline` — AI edit entry:
   - L52: `internalBase = timelineConversionService.ensureInternalTimelineJson(baseTimelineJson)`
   - L54-55: `AiTimelineEditResponseParser.parse(ai.content(), timelineSpecResolver)`
   - L60-68: human-in-the-loop → appendPendingPatchProposal(internalBase, instruction, patch.operations())
   - L70: else `resultJson = applyParsed(internalBase, parsed)`
5. AiTimelineEditResponseParser.java:
   - L76-82: if `resolver.isInternalTimelineJson(trimmed)` → `Parsed.fullTimeline(trimmed)` — **the AI-generated JSON is returned verbatim with ZERO field filtering**
   - L84-91: `{ "timelineJson": ... }` wrapper → fullTimeline(inner)
   - L93-97: `{ "operations": [...] }` → patchOps(parseOperations(...))
   - L89: `record FullTimeline(String timelineJson)` — carries arbitrary internal-1.0 JSON
6. AiTimelineEditService.applyParsed L117-131:
   - FullTimeline → `return full.timelineJson();` — **no schema restriction beyond isInternalTimelineJson**
   - PatchOps → `timelinePatchService.applyPatch(base, patch.operations())`
7. TimelinePatchService L73: `if (path == null || !path.startsWith("/"))` — **path validation is a bare "/" prefix check; there is NO whitelist restricting patch paths** → patch operations can target /transitions, /automations, /effects/*/automation.
8. InternalTimelineJson.isInternalTimeline: accepts any object with schemaVersion starting "1" OR a composition object — does not exclude transitions/automations.
9. TimelineCanonicalValidator L40-49, L176-259: validates transitions (validateTransitionReferences) and automations (validateAutomationTargets) as first-class candidate fields — **internal-1.0 schema (and its validation) explicitly supports transitions and automations**.
10. TimelineDocumentCandidateMapper L48: "carries no transitions/automations fields; those live in internal" — **canonical TimelineDocument cannot represent transitions or automations**.
11. TimelineRevisionSaveService.saveRevisionWithEffects: accepts TimelineDocument + EffectInstance/EffectDefinition lists; persists TimelineDocument-derived snapshot. No transitions/automations channel.

## Conclusions (positive type/value-flow evidence)

- CAN_AI_PATH_AUTHOR_TRANSITIONS = YES
  Evidence: fullTimeline path returns AI JSON verbatim (parser L82, applyParsed L118-119);
  internal-1.0 schema + validator support transitions (validator L44, L176-203);
  isInternalTimelineJson does not exclude them; patch ops can target /transitions (patch path check L73 is prefix-only).
- CAN_AI_PATH_AUTHOR_TIMELINE_AUTOMATION = YES
  Evidence: same fullTimeline verbatim path; validator L49, L230-259 validates automations as first-class;
  patch ops can target /automations.
- CAN_AI_PATH_AUTHOR_EFFECT_AUTOMATION = YES
  Evidence: effect automation lives under clip.effects[].automation in internal-1.0 (adapter mapEffects);
  fullTimeline returns such JSON verbatim; patch ops can target /tracks/*/clips/*/effects/*/automation.

## Losslessness determination

- Canonical migration target: TimelineRevisionSaveService.saveRevisionWithEffects(TimelineDocument, effects, definitions).
- TimelineDocument cannot carry transitions/automations/effect-automation (TimelineDocumentCandidateMapper L48; TimelineDocument fields: tracks, audioMix, textElements, semanticRelationships, metadata — no transitions/automations).
- AI path CAN author all three categories (proven above).
- Therefore migration would DROP authorable semantics → LOSSLESS_MIGRATION_PROOF = FAIL.

## Final disposition (per CFRH clean-forward rule: no lossy migration)

recordAiAdoptRevision = DELETE_OBSOLETE_PRODUCT_BEHAVIOR

- The legacy AI-adopt write path (persisting AI-adopted revisions through
  recordAiAdoptRevision) is deleted.
- AI EDITING product behavior is NOT deleted: AiTimelineEditService.editTimeline /
  editFromBaseJob / ai-edit endpoint remain (they produce/return edited
  timeline JSON without legacy revision persistence).
- Future canonical persistence of AI-edited timelines (if authorized) must
  enter through canonical TimelineRevisionSaveService with a complete
  transitions/automations representation decision — OUT OF I1 SCOPE.
- No lossy conversion is performed. No compatibility wrapper is created.
- No frozen canonical type is modified.
