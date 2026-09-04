#!/usr/bin/env python3
"""Fail-closed structural guard for the H8 Operation invocation boundary."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


FROZEN_EXECUTABLE_DEFINITIONS = {
    "ADD_MEDIA_CLIP": "timeline.media-clip.add@1.0",
}

H8_PRE_CANONICAL_BASE_SHA = "b82b0dadfbee56e0436c7623e8ebc18971dc953a"
H8_ACCEPTED_CANONICAL_SHA = "16e0022e91e384fc05dfd8497c29640c8deec195"

H8_AUTHORIZED_CHANGED_PATHS = frozenset({
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationContext.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationException.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationFailureCode.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationPort.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationResult.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionId.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionVersion.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationParameters.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationRequest.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationTargetRequest.java",
    "operation-module/src/test/java/com/example/platform/operation/invocation/OperationInvocationContractTest.java",
    "render-module/src/main/java/com/example/platform/render/app/operation/CanonicalOperationInvocationService.java",
    "render-module/src/main/java/com/example/platform/render/app/operation/TimelineMediaClipOperationService.java",
    "render-module/src/main/java/com/example/platform/render/package-info.java",
    "render-module/src/test/java/com/example/platform/render/app/operation/CanonicalOperationInvocationArchitectureTest.java",
    "render-module/src/test/java/com/example/platform/render/app/operation/CanonicalOperationInvocationServiceTest.java",
    "render-module/src/test/java/com/example/platform/render/app/operation/H7FirstRealMediaCutTest.java",
    "render-module/src/test/java/com/example/platform/render/app/operation/H8OperationInvocationBoundaryGuardTest.java",
    "scripts/check-architecture-drift.sh",
    "scripts/guards/h8-operation-invocation-boundary-guard.py",
    "workflow-module/src/main/java/com/example/platform/workflow/package-info.java",
    "workflow-module/src/test/java/com/example/platform/workflow/architecture/OperationInvocationBoundaryArchitectureTest.java",
})

REQUIRED_LAW_COUNT = 54
REQUIRED_GOVERNED_RUNTIME_SOURCE_COUNT = 91

# Exact SHA-256 attestation of the committed e570cf93 H8/H7 runtime authority
# universe. These repository-relative paths cover the exposed Operation intent
# and invocation contracts, the canonical adapter/coordinator/apply/writer
# boundaries, and the exact direct authority dependencies that can change
# dispatch, base, authorization, result, or canonical-writer semantics.
GOVERNED_RUNTIME_SOURCE_SHA256 = {
    "artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactPinService.java": "34f5b57c43c1c8710adaf01c1166d491ec5b7e31172cda555cf4fde438e1d348",
    "media-module/src/main/java/com/example/platform/media/domain/identity/MediaAssetId.java": "4512f45c34dc5d502c424a585a9330eef99680ebe31d6f9ba23df09316679e0e",
    "media-module/src/main/java/com/example/platform/media/domain/stream/MediaStreamId.java": "3b5ee2acae0e44de0a3e464ef89255bf39cc6e998efbae0cb1de06af9e846488",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationContext.java": "e6958af12ac930af2fcefdf501a0906528352896f883d4f7810f7cd9030cf881",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationException.java": "5762eae13a5cf2f4ffb557896f13ae894dfd2e717206a75c1f0db3d02edb1e89",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationFailureCode.java": "bfd5bb23139b8b8d98bd6db3617faa981834bbd2aad703cc853e07ca735dd744",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationPort.java": "70e5190dcb265eda47656152575a9e17262a0bc06f2b7218d338ac3100685d74",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationResult.java": "fafc52a4cf555fdf5380fefcd1b7c51e52a71a11550f19a8bd00c926ee917ff4",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinition.java": "83e90f649fdb6994ea2a907dbe8f123b5e3710822ea289a7df80cb3ba20637f7",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionId.java": "2b8f1b6fb269f4008b056fbbf399bcd98b5826b8d670cfa77fa71be842b028eb",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionVersion.java": "4fd8c29fcb4ab015b32d83f7b364fa527e1ff2936ad3be71a5ce244c76e197ce",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationErrorCode.java": "17262c17066f4f6334edbfbe32ba96bef9280a7436c30e8b803ff1ba0d6090dc",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationInstance.java": "a6dece0869b9fec2b6354903b2eb55e5492042fa42591dbe5f486b4076b36527",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationParameters.java": "371f88aaa050fef325e3a6612344ac77f081685b4fc51f4a60b92a7d30c5871c",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationRequest.java": "ddda77c0e58bacd6de5d5e58aa93e5f690a10c1d110cc1d70540142a31def649",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationRequestResolver.java": "d5ebfa82b1e5cb599b265415dc40558a90aa25c013d11e023c0365fc21d0a96c",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationResolutionException.java": "da3fdef0dbf21368ae33fe8997330ffdeb36dbf189ba484c646019eee8cb4bc9",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationTargetRequest.java": "36968a5a3ce52dfa180297fb1014dbcfb4f5b4498a8d48911a7c515117e8193e",
    "operation-module/src/main/java/com/example/platform/operation/plan/ApplyContext.java": "62d3064356086d5751485f51c194cd8eb324f608741d050636b30c9bbb59d4a6",
    "operation-module/src/main/java/com/example/platform/operation/plan/ApplyResult.java": "4da8239c2b3af825e091bcc11728b390f4f2a37ccbac95170e83efdf6b60c151",
    "operation-module/src/main/java/com/example/platform/operation/plan/AuthorizationDecision.java": "5487e2edf1e25e917ce58dc699f8d5b80c169e4cc14320488d0dc52a9a4ed11a",
    "operation-module/src/main/java/com/example/platform/operation/plan/OperationPlan.java": "e321afd0095f42b7c08e16f87452ed20f40c3f55ac17295d9d6baf8cb68c4987",
    "operation-module/src/main/java/com/example/platform/operation/plan/OperationPlanDigest.java": "04134c8a6d59261e9ad24a92100a8b634f17f1dfe2716434c010c43c86e249b8",
    "operation-module/src/main/java/com/example/platform/operation/plan/OperationPlanPreview.java": "32adb6da181a68ff3335b76715a0adcecee3a19a0a81dde9465e383765b02f5c",
    "operation-module/src/main/java/com/example/platform/operation/plan/OperationPlanner.java": "1ce990a7974448cfca905b4464cc6ce7ae6b10da6fb4c4d67a7598d7479727b8",
    "operation-module/src/main/java/com/example/platform/operation/plan/PlanErrorCode.java": "e117ef4e0a02b7a33bef8871a7274a6b6942bf4388a322489410dba32c6d427f",
    "operation-module/src/main/java/com/example/platform/operation/plan/PlanException.java": "36634a69334699b967da9d633103957125e5c4f071d0f28c81ae86229726a26e",
    "operation-module/src/main/java/com/example/platform/operation/plan/TargetRevisionRef.java": "35d4392cf7189c70afd90949fe421dfabfdb21e8e2eaa469240460ee71ed80ec",
    "render-module/src/main/java/com/example/platform/render/app/operation/AddMediaClipCommand.java": "da4359b9591b4f04f46de008d4972a9d60fb2c2f9ffd632f64e5a7792ab6e1f5",
    "render-module/src/main/java/com/example/platform/render/app/operation/AddMediaClipPreview.java": "64d37917bdc866d6a842d981e60e8a013f0d1cd77c3e678a6eb8b22ce062474c",
    "render-module/src/main/java/com/example/platform/render/app/operation/AddMediaClipResult.java": "58a0ed832754c16151e45382d853dc16f86767dbd92b998c2e18e7d6ef9df7de",
    "render-module/src/main/java/com/example/platform/render/app/operation/CanonicalOperationInvocationService.java": "3ab1c0d6f44151d3017a834c0461b17677ae7945c4ccef583d2551d8a4657507",
    "render-module/src/main/java/com/example/platform/render/app/operation/TimelineMediaClipOperationService.java": "b48fcd9c11203fdaeca071e43f4a5eefe2cad649067210d1c74f50b60d63ea33",
    "render-module/src/main/java/com/example/platform/render/app/operation/TimelineOperationException.java": "5d2a381fab95085537c6e50db781f96a540ba706ecbea9831a8a545438fa7195",
    "render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java": "3db037395ff70b2f8c27abb3101bc4bcd00ce9505f257a0954b608e446cbed5e",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizableResourceRef.java": "0d5480a73113dad46a460df734c8f78e2af85f0a29a105dc6bde729dc94a4dfd",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationAction.java": "f3e24e6788c4282e429b77c20a658ed4f41e399a1eee673ec941f837e5728b49",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationContext.java": "e83057cb025fa05a001732e1dd0ae83ac81478e1f1807dafae03529ddb41b6f0",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationDecision.java": "f91212d6ab675f6b79630066a5fb2c4b835fce5d4e2aeee5c3555e46f8617b6a",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationDecisionPort.java": "e77e2da54652a27d6d80b1acf1d7ace27d2377d45d2fe9d6cae4364ac3024e4d",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationRequest.java": "7c684da3228dcbb1ae02828c7bd2ea4eb6828f02b03ed4a27649c797c34d6d1a",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/AuthorizationResourceType.java": "93507e3282ba2e3bb3129912f233ef6528bcf58014cf0b916f8dc3df3f0324b9",
    "shared-kernel/src/main/java/com/example/platform/shared/authorization/CanonicalActor.java": "3438cb431bd0d66b00c26d764858549d42d6244eb4eebf20c459ce0956648b90",
    "shared-kernel/src/main/java/com/example/platform/shared/digest/CanonicalCommandFingerprint.java": "2806a006f7f840ffdcc9b3fe7d7545f5455534646e52aef759247c79f240707c",
    "shared-kernel/src/main/java/com/example/platform/shared/digest/ContentDigest.java": "9b909f58acf190e50ff2ac1c47a9c305e8780173f1d8db8e5e96d34fc0fd9d14",
    "shared-kernel/src/main/java/com/example/platform/shared/identity/ArtifactId.java": "84ba3f0930448050e7bb462999f0c6c0c2733d756a323c887c2b9edbfa6d5f97",
    "shared-kernel/src/main/java/com/example/platform/shared/time/MediaTime.java": "623b6c99ad371987fb9fceb427e7c8290e0cb4eac6f36dc10a2a70c8a1d33942",
    "timeline-module/src/main/java/com/example/platform/timeline/adapter/TimelineSnapshotService.java": "1fde2d9a724b31164e3281750ebb8be4604bbb8996a44e5744dd6d08eae01e4a",
    "timeline-module/src/main/java/com/example/platform/timeline/app/HeadUpdatePort.java": "97a4ef46cb8c7aaf87d421ee40f1bf638d53573f40e2ea3a7e1873c49641f911",
    "timeline-module/src/main/java/com/example/platform/timeline/app/HistoricalRevisionRestoreVerifier.java": "a808b4804be60cec2c23f5ddd8a92589c124c05f126960530265b9bc0c6270a5",
    "timeline-module/src/main/java/com/example/platform/timeline/app/InternalTimelineValidationService.java": "2a99d221b7b43b4aff00ad782874e83479f8743e17c1c71929e36e6d62521156",
    "timeline-module/src/main/java/com/example/platform/timeline/app/ProjectRevisionNumberAllocator.java": "8e46f9f42522f34c79b4b7c32a19815aa53d1971a2c98e322c82717a02179de2",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinExtractor.java": "497795ff8b1c0fb10c382385a08ee17c25f4c7ab17f6fbafda4a9d27058d93bc",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineArtifactPinValidator.java": "e250c3bd8e2476f8d8b5794c386d4dde6431d613081e6f69df6fe83863668059",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineCanonicalRejectionException.java": "5107132400d434c285069426eefe9bf684cef550c534dbf988a7c2377da51e8d",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineDocumentCandidateMapper.java": "0458692bca9e8d0deac651f54efa9f110b30014ce0a5c22a51d71f3ebfbe1a91",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineDocumentJsonSerializer.java": "85044834384aa18dd155ed0da7f7bab3d618690c8b4dfbc08e86697730bb963f",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineMutationContext.java": "12925ce84c12420dba3df8dbc30849cc7cd082f080e2751c1c4aee9864e67c52",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionCommandConflictException.java": "6f598d9e4424ac140117881d90902588ef6f4b874c9be04de9ce243b7a2132f1",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionPersistencePort.java": "75687b44fa1bcc7881b75077dd376adafe481dcddd2d5caef163a9e25bae87d7",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionRefHeadUpdateAdapter.java": "6aaac36d7e5dbc73100d04c1ab6c6b8adceeb9c1c801cedcd1f440f6db54ea9b",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionRefMutation.java": "9cde6e2dc5d8f90e6903fd5a755c6a55406659cd85ab58ea08f61ee78d00389c",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionSaveService.java": "ed0d2ff6c6b7d73d51acf7ceb54fb66a5f7fc3a6b1ca438557ef1e4086ab3245",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineSourceReferenceValidator.java": "0f322c14fb89c3a577b66c432e9f4785db09882024e934ad1584ea214b112185",
    "timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineClip.java": "c3284017dbdf634144962e0e5df52ff503c9309fb65e8ca293242e0b4ac22d6c",
    "timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineClipId.java": "fbbe75799ec35aedd0fa7c98024185bb7b49a76dee73ee1b16f6711b90a7dd1a",
    "timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineContentDigester.java": "7410cacc54ef86b99f1ff3d2691f2ac4a1eb593f64e5f3ebbfc1864462eb4324",
    "timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineDocument.java": "f5a6ef2a78afe174e848485028e691c36747cb4708ef787c5e658a891d839971",
    "timeline-module/src/main/java/com/example/platform/timeline/canonical/TimelineTrack.java": "5cd583b5cc432567a92425eddf16b84b7663bbe8a29ebcc866cdb5f1245c6c9c",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCandidate.java": "4039473937cce481057cadd58a34a917eefd2dba11e367267081c39f19b65699",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCanonicalNormalizer.java": "010ca5883b0c2bed72eaef109bfa112dc5af46ba903a90c9c4833c42b27cf339",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineCanonicalValidator.java": "28bc858bcf957d4239156439e533fc681c049cf0267e632ddaf7c64eac9680ac",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineDiagnostic.java": "66f8c3163571ac4226f62ba55eb64cec0ffe743330a603f08dd504779c2a0a1a",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineModelPath.java": "f02daf20d7c4d927dcd0ae6ad740ef9d4073b707f0e5ec4628293d5ad762236c",
    "timeline-module/src/main/java/com/example/platform/timeline/canonicalmodel/TimelineValidationResult.java": "52fc87b77567e1bb4abf8f836aa0b7315e2ece4069f14e68357a482b5fa469df",
    "timeline-module/src/main/java/com/example/platform/timeline/revisioncommand/RevisionRef.java": "72b9e22953d2b05910b29cda06481d60294a8ad2d3ff3fa92912b136ace0597e",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/clip/MediaClip.java": "f310a1c012ada9f0fe1f3bc19115265a3c3944d3e645709de563e206fffd8440",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/clip/MediaStreamSourceBinding.java": "ea525f6cd9d8350d1b986db1bbdb15d416fc75e66293c78fbf7c75a6ac991a0a",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectInstance.java": "2cd3000310ae06efd56358c00c10584f77ff85f85ee2e478c60c453401d0e804",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshot.java": "559794ceb2764daf4161d7989897c7819883d90520a7c2b1564dd8834d6b5d62",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotAuthority.java": "b6a7682d841aa8365a6dac6e913c9b7b9b4dd4b685003467cd1da51df61d20b0",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotReference.java": "dbdbb15835d1a2d23c4da8776f9c7a6b6b1df2155bc29d7e57c36b04f6b06b61",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/TimelineRevisionEffectSemanticCommitment.java": "277661895f1a5473807dcbee8d5ad7a672ce21bac761b56ddfcc88cc4acded23",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/temporal/ConstantRateTemporalMapping.java": "a26da6bfe5cd5f43ff860dd02ba91b5acb99111f25540297736c5ec493efeb47",
    "timeline-module/src/main/java/com/example/platform/timeline/semantics/temporal/PlaybackDirection.java": "24493f091aed65b4f6d491528c21652d5c202fc4cb05bdf045660b38d9ab692e",
    "timeline-module/src/main/java/com/example/platform/timeline/version/TimelineConflictException.java": "f8cabdf06fb444ec09a2d45977a84b062768170d8ee5d38964296f6a1e4703b4",
    "timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevision.java": "07584f20a4173746f0b5697950ee50289b4943bb996049a7d5142887f0afa2ed",
    "timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContext.java": "ceb562976747d21c827e9c12d81beb2c0bd51d44dd75d2ebf4e9b32b736ef2ca",
    "timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContextStore.java": "704b4a78ddde04bb155d8f6a187dd23eb3d0221603c1197700e23881ca7e38db",
    "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/tables/TimelineRevision.java": "1dca8683c537e78af0b9853ec9aa698fdbcfecc47399cbd62195a0a960508c83",
    "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/tables/TimelineRevisionParent.java": "e013952a0f9ad7b702fdadfc4124b99d109a9e61f847700fef4ab2704bd2362c",
}

LAW_ORDER = (
    "OPERATION_REQUEST_AUTHORITY_COUNT",
    "NEW_OPERATION_REQUEST_PEER_TYPE_COUNT",
    "OPERATION_INVOCATION_PORT_AUTHORITY_COUNT",
    "OPERATION_INVOCATION_PORT_IMPLEMENTATION_COUNT",
    "WORKFLOW_PEER_INVOCATION_PORT_COUNT",
    "WORKFLOW_PEER_OPERATION_RESULT_AUTHORITY_COUNT",
    "LEGACY_WORKFLOW_INVOCATION_PORT_DEFINITION_COUNT",
    "LEGACY_WORKFLOW_OPERATION_RESULT_DEFINITION_COUNT",
    "WORKFLOW_OPERATION_PLAN_IMPORT_COUNT",
    "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT",
    "WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT",
    "WORKFLOW_GENERIC_TIMELINE_PATCH_USAGE_COUNT",
    "WORKFLOW_JOOQ_IMPORT_COUNT",
    "WORKFLOW_CANONICAL_MEDIA_WRITER_COUNT",
    "WORKFLOW_OPERATION_RESULT_REAUTHORING_COUNT",
    "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT",
    "WORKFLOW_TO_OPERATION_UNEXPOSED_DEPENDENCY_COUNT",
    "WORKFLOW_OPERATION_INVOCATION_ALLOWED_DEPENDENCY_COUNT",
    "WORKFLOW_BROAD_OPERATION_ALLOWED_DEPENDENCY_COUNT",
    "BROAD_OPERATION_PACKAGE_EXPOSURE_COUNT",
    "FORBIDDEN_INVOCATION_PUBLIC_SIGNATURE_TYPE_COUNT",
    "REQUEST_CONTROLLED_ACTOR_AUTHORITY_COUNT",
    "REQUEST_CONTROLLED_ADMIN_AUTHORITY_COUNT",
    "DUPLICATE_TENANT_AUTHORITY_COUNT",
    "WORKFLOW_PROVENANCE_AS_CANONICAL_AUTHORITY_COUNT",
    "H8_PROVIDER_BINDING_FIELD_COUNT",
    "H8_WORKER_BINDING_FIELD_COUNT",
    "H8_DEVICE_BINDING_FIELD_COUNT",
    "H8_STORAGE_IMPLEMENTATION_IMPORT_COUNT",
    "MUTABLE_LATEST_FALLBACK_COUNT",
    "REFLECTION_OPERATION_EXECUTION_FALLBACK_COUNT",
    "GENERIC_OPERATION_PLANNER_FALLBACK_COUNT",
    "EXECUTABLE_OPERATION_DEFINITION_COUNT",
    "DUPLICATE_OPERATION_INVOCATION_RESULT_AUTHORITY_COUNT",
    "DUPLICATE_OPERATION_INVOCATION_FAILURE_AUTHORITY_COUNT",
    "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT",
    "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT",
    "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",
    "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT",
    "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT",
    "H8_SCHEMA_MUTATION_AUTHORITY_COUNT",
    "H8_JOOQ_GENERATED_MUTATION_AUTHORITY_COUNT",
    "CAMELCASE_SCHEMA_MARKER_ESCAPE_COUNT",
    "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT",
    "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",
    "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",
    "H8_NEW_TIMELINE_WRITER_COUNT",
    "H8_NEW_HEAD_AUTHORITY_COUNT",
    "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT",
    "CANONICAL_TIMELINE_HEAD_AUTHORITY_COUNT",
    "CANONICAL_TIMELINE_MUTATION_WRITER_AUTHORITY_COUNT",
    "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT",
    "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
    "UNCLASSIFIED",
)

ONE_LAWS = {
    "OPERATION_REQUEST_AUTHORITY_COUNT",
    "OPERATION_INVOCATION_PORT_AUTHORITY_COUNT",
    "OPERATION_INVOCATION_PORT_IMPLEMENTATION_COUNT",
    "WORKFLOW_OPERATION_INVOCATION_ALLOWED_DEPENDENCY_COUNT",
    "EXECUTABLE_OPERATION_DEFINITION_COUNT",
    "CANONICAL_TIMELINE_HEAD_AUTHORITY_COUNT",
    "CANONICAL_TIMELINE_MUTATION_WRITER_AUTHORITY_COUNT",
}

CONTRACT_SUFFIXES = (
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationPort.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationContext.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationResult.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationFailureCode.java",
    "operation-module/src/main/java/com/example/platform/operation/invocation/OperationInvocationException.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationRequest.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionId.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinitionVersion.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationTargetRequest.java",
    "operation-module/src/main/java/com/example/platform/operation/operation/OperationParameters.java",
)

CONTRACT_FQ_TYPES = {
    suffix.rsplit("/", 1)[-1].removesuffix(".java"): (
        "com.example.platform.operation.invocation."
        if "/invocation/" in suffix
        else "com.example.platform.operation.operation.")
    + suffix.rsplit("/", 1)[-1].removesuffix(".java")
    for suffix in CONTRACT_SUFFIXES
}

# Runtime roles are declared by Java type, not directory. This keeps the
# universe location-independent while making every authority-bearing role an
# explicit guard decision. The Timeline writer/ref types are lower canonical
# boundaries: they are classified so traversal cannot mistake them for an H8
# helper, but their existing authority is not transferred to H8.
CLASSIFIED_RUNTIME_FQ_TYPES = {
    "com.example.platform.operation.invocation.OperationInvocationPort": "public-contract",
    "com.example.platform.operation.invocation.OperationInvocationContext": "public-contract",
    "com.example.platform.operation.invocation.OperationInvocationResult": "public-result-authority",
    "com.example.platform.operation.invocation.OperationInvocationFailureCode": "public-failure-authority",
    "com.example.platform.operation.invocation.OperationInvocationException": "public-failure-authority",
    "com.example.platform.operation.operation.OperationRequest": "public-contract",
    "com.example.platform.operation.operation.OperationDefinitionId": "public-contract",
    "com.example.platform.operation.operation.OperationDefinitionVersion": "public-contract",
    "com.example.platform.operation.operation.OperationTargetRequest": "public-contract",
    "com.example.platform.operation.operation.OperationParameters": "public-contract",
    "com.example.platform.render.app.operation.CanonicalOperationInvocationService": "canonical-adapter",
    "com.example.platform.render.app.operation.TimelineMediaClipOperationService": "delegated-coordinator",
    "com.example.platform.render.app.operation.TimelineOperationException": "internal-delegated-failure",
    "com.example.platform.render.app.plan.OperationPlanApplyService": "lower-apply-boundary",
    "com.example.platform.timeline.app.TimelineRevisionSaveService": "canonical-timeline-writer",
    "com.example.platform.timeline.app.HeadUpdatePort": "canonical-head-port",
    "com.example.platform.timeline.app.TimelineRevisionRefMutation": "canonical-head-authority",
    "com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter": "canonical-head-adapter",
}

# These are the existing operation mechanics reached by the delegated H7
# coordinator. They are classified dependencies, never public H8 authority.
CLASSIFIED_OPERATION_MECHANICS_FQ_TYPES = {
    "com.example.platform.operation.operation.OperationErrorCode",
    "com.example.platform.operation.operation.OperationInstance",
    "com.example.platform.operation.operation.OperationRequestResolver",
    "com.example.platform.operation.plan.ApplyResult",
    "com.example.platform.operation.plan.OperationPlan",
    "com.example.platform.operation.plan.OperationPlanDigest",
    "com.example.platform.operation.plan.OperationPlanPreview",
    "com.example.platform.operation.plan.OperationPlanner",
}

# Exact direct production dependencies of the canonical adapter, delegated H7
# coordinator, and lower apply boundary. A newly called production helper has
# to be classified here deliberately; otherwise UNCLASSIFIED fails closed.
KNOWN_RUNTIME_DIRECT_DEPENDENCY_FQ_TYPES = {
    "com.example.platform.media.domain.identity.MediaAssetId",
    "com.example.platform.media.domain.stream.MediaStreamId",
    "com.example.platform.operation.invocation.OperationInvocationContext",
    "com.example.platform.operation.invocation.OperationInvocationException",
    "com.example.platform.operation.invocation.OperationInvocationFailureCode",
    "com.example.platform.operation.invocation.OperationInvocationPort",
    "com.example.platform.operation.invocation.OperationInvocationResult",
    "com.example.platform.operation.operation.OperationDefinition",
    "com.example.platform.operation.operation.OperationErrorCode",
    "com.example.platform.operation.operation.OperationInstance",
    "com.example.platform.operation.operation.OperationParameters",
    "com.example.platform.operation.operation.OperationRequest",
    "com.example.platform.operation.operation.OperationRequestResolver",
    "com.example.platform.operation.operation.OperationResolutionException",
    "com.example.platform.operation.operation.OperationTargetRequest",
    "com.example.platform.operation.plan.ApplyContext",
    "com.example.platform.operation.plan.ApplyResult",
    "com.example.platform.operation.plan.AuthorizationDecision",
    "com.example.platform.operation.plan.OperationPlan",
    "com.example.platform.operation.plan.OperationPlanPreview",
    "com.example.platform.operation.plan.OperationPlanner",
    "com.example.platform.operation.plan.PlanErrorCode",
    "com.example.platform.operation.plan.PlanException",
    "com.example.platform.operation.plan.TargetRevisionRef",
    "com.example.platform.render.app.operation.AddMediaClipCommand",
    "com.example.platform.render.app.operation.AddMediaClipPreview",
    "com.example.platform.render.app.operation.AddMediaClipResult",
    "com.example.platform.render.app.operation.CanonicalOperationInvocationService",
    "com.example.platform.render.app.operation.TimelineMediaClipOperationService",
    "com.example.platform.render.app.operation.TimelineOperationException",
    "com.example.platform.render.app.plan.OperationPlanApplyService",
    "com.example.platform.shared.authorization.AuthorizableResourceRef",
    "com.example.platform.shared.authorization.AuthorizationAction",
    "com.example.platform.shared.authorization.AuthorizationContext",
    "com.example.platform.shared.authorization.AuthorizationDecision",
    "com.example.platform.shared.authorization.AuthorizationDecisionPort",
    "com.example.platform.shared.authorization.AuthorizationRequest",
    "com.example.platform.shared.authorization.AuthorizationResourceType",
    "com.example.platform.shared.authorization.CanonicalActor",
    "com.example.platform.shared.digest.CanonicalCommandFingerprint",
    "com.example.platform.shared.digest.ContentDigest",
    "com.example.platform.shared.identity.ArtifactId",
    "com.example.platform.shared.time.MediaTime",
    "com.example.platform.timeline.app.HeadUpdatePort",
    "com.example.platform.timeline.app.InternalTimelineValidationService",
    "com.example.platform.timeline.app.TimelineCanonicalRejectionException",
    "com.example.platform.timeline.app.TimelineMutationContext",
    "com.example.platform.timeline.app.TimelineRevisionCommandConflictException",
    "com.example.platform.timeline.app.TimelineRevisionSaveService",
    "com.example.platform.timeline.app.TimelineSourceReferenceValidator",
    "com.example.platform.timeline.canonical.TimelineClipId",
    "com.example.platform.timeline.canonical.TimelineContentDigester",
    "com.example.platform.timeline.canonical.TimelineDocument",
    "com.example.platform.timeline.canonical.TimelineTrack",
    "com.example.platform.timeline.canonicalmodel.TimelineDiagnostic",
    "com.example.platform.timeline.revisioncommand.RevisionRef",
    "com.example.platform.timeline.semantics.clip.MediaClip",
    "com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding",
    "com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping",
    "com.example.platform.timeline.semantics.temporal.PlaybackDirection",
    "com.example.platform.timeline.version.TimelineConflictException",
    "com.example.platform.timeline.version.TimelineRevision",
}

INTERNAL_RESULT_PROJECTIONS = {
    "com.example.platform.render.app.operation.TimelineMediaClipOperationService": {
        "InvocationOutcome", "ExecutionOutcome",
    },
}
INTERNAL_FAILURE_PROJECTIONS = {
    "com.example.platform.render.app.operation.TimelineOperationException": {
        "TimelineOperationException", "Code",
    },
}

CANONICAL_INVOCATION_RESULT_OWNER = (
    "com.example.platform.operation.invocation.OperationInvocationResult")
CANONICAL_INVOCATION_FAILURE_OWNERS = {
    "com.example.platform.operation.invocation.OperationInvocationFailureCode",
    "com.example.platform.operation.invocation.OperationInvocationException",
}

CENTRAL_FLYWAY_ROOT = "platform-app/src/main/resources/db/migration/"
CENTRAL_SCHEMA_GENERATION_ROOT = "typed-schema-module/"
CENTRAL_GENERATED_JOOQ_ROOT = (
    "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/"
)

FORBIDDEN_SIGNATURE_TYPES = (
    "OperationRequestResolver", "OperationInstance", "OperationDefinition", "OperationRegistry",
    "OperationPlanner", "OperationPlan", "OperationPlanPreview", "PlannedChange", "ApplyContext",
    "AuthorizationDecision", "ApplyResult", "OperationPlanApplyService", "TimelineRevisionSaveService",
    "TimelinePatch", "TimelineRevisionPersistence", "TimelineRevisionRepository", "HeadUpdatePort",
    "TimelineRevisionRefMutation", "DSLContext", "org.jooq", "Provider", "Worker", "Runtime",
)

WORKFLOW_ALLOWED_OPERATION_IMPORTS = {
    "com.example.platform.operation.invocation.OperationInvocationPort",
    "com.example.platform.operation.invocation.OperationInvocationContext",
    "com.example.platform.operation.invocation.OperationInvocationResult",
    "com.example.platform.operation.invocation.OperationInvocationFailureCode",
    "com.example.platform.operation.invocation.OperationInvocationException",
    "com.example.platform.operation.operation.OperationRequest",
    "com.example.platform.operation.operation.OperationDefinitionId",
    "com.example.platform.operation.operation.OperationDefinitionVersion",
    "com.example.platform.operation.operation.OperationTargetRequest",
    "com.example.platform.operation.operation.OperationParameters",
}

TYPE_DECLARATION = re.compile(
    r"\b(?:public\s+)?(?:final\s+|sealed\s+|non-sealed\s+|abstract\s+)*"
    r"(?:class|interface|record|enum)\s+([A-Za-z_$][A-Za-z0-9_$]*)")
TYPE_DECLARATION_WITH_MODIFIERS = re.compile(
    r"\b(?P<modifiers>(?:(?:public|protected|private|static|final|sealed|"
    r"non-sealed|abstract)\s+)*)"
    r"(?:class|interface|record|enum)\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)")
PUBLIC_TYPE_DECLARATION = re.compile(
    r"\bpublic\s+(?:final\s+|sealed\s+|non-sealed\s+|abstract\s+)*"
    r"(?:class|interface|record|enum)\s+([A-Za-z_$][A-Za-z0-9_$]*)")
ANNOTATED_PUBLIC_TYPE = re.compile(
    r"@(?:org\.springframework\.modulith\.)?NamedInterface\(\s*\"invocation\"\s*\)\s*"
    r"public\s+(?:final\s+|sealed\s+|non-sealed\s+|abstract\s+)*"
    r"(?:class|interface|record|enum)\s+([A-Za-z_$][A-Za-z0-9_$]*)")
IMPORT = re.compile(r"(?m)^\s*import\s+([^;]+);")
PACKAGE = re.compile(r"(?m)^\s*package\s+([A-Za-z_$][\w.$]*)\s*;")
JAVA_WORD = re.compile(r"\b[A-Za-z_$][A-Za-z0-9_$]*\b")
JAVA_QUALIFIED_NAME = re.compile(
    r"\b(?:[a-z_$][A-Za-z0-9_$]*\.)+[A-Z_$][A-Za-z0-9_$]*"
    r"(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*")
JAVA_COMMENT_OR_LITERAL = re.compile(
    r'(\"\"\".*?\"\"\")|(\"(?:\\.|[^\"\\])*\")|'
    r"('(?:\\.|[^'\\])*')|(/\*.*?\*/|//[^\n]*)",
    flags=re.DOTALL)

DIRECT_PREPARATION_AUTHORIZATION = re.compile(
    r"requirePreparationAuthorization\s*\(\s*tenantId\s*,\s*projectId\s*,\s*"
    r"actor\s*,\s*TIMELINE_READ\s*\)\s*;",
    flags=re.DOTALL)
JAVA_CALL = re.compile(
    r"\b([A-Za-z_$][A-Za-z0-9_$]*"
    r"(?:\s*\.\s*[A-Za-z_$][A-Za-z0-9_$]*)*)\s*\(")
AUTHORIZATION_HELPER_ALLOWED_CALLS = {
    "Objects.requireNonNull",
    "Objects.equals",
    "actor.tenantId",
    "TimelineOperationException",
    "List.of",
    "authorizationPort.decide",
    "AuthorizationRequest",
    "AuthorizableResourceRef",
    "AuthorizationContext",
    "Map.of",
    "decision.allowed",
}
PREPARATION_MECHANIC = re.compile(
    r"\b(?:prepare|prepareInternal|toOperationRequest|findById|findPayloadDocument|"
    r"timelineContentDigest|resolve|validate|plan|apply|findLatest|loadLatest|"
    r"resolveLatest|findCurrent|loadCurrent|readRef|writeRef|saveRevision|"
    r"saveRevisionForCommand|insertRevision|advanceHead|updateHead|moveRef|setHead)\s*\(|"
    r"\b(?:revisionSaveService|sourceValidator|timelineValidator|planner|applyService|"
    r"OperationRequestResolver|OperationPlanner|OperationPlanApplyService|"
    r"TimelineRevisionSaveService|TimelineSourceReferenceValidator|"
    r"InternalTimelineValidationService|TimelineRevisionRepository|"
    r"TimelineRevisionPersistence|TimelineRevisionRefMutation|HeadUpdatePort)\b")


@dataclass(frozen=True)
class Evaluation:
    counts: dict[str, int]
    details: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return evaluation_passes(self.counts, LAW_ORDER)


def law_definition_errors(law_order: tuple[str, ...]) -> tuple[str, ...]:
    """Validate the executable law census independently of an evaluation."""
    errors: list[str] = []
    if len(law_order) != REQUIRED_LAW_COUNT:
        errors.append(
            f"law census size {len(law_order)} does not equal {REQUIRED_LAW_COUNT}")
    if len(set(law_order)) != len(law_order):
        errors.append("law census contains duplicate names")
    missing_expectations = ONE_LAWS - set(law_order)
    if missing_expectations:
        errors.append("one-valued laws missing: " + ",".join(sorted(missing_expectations)))
    return tuple(errors)


def evaluation_passes(counts: dict[str, int], law_order: tuple[str, ...]) -> bool:
    return not law_definition_errors(law_order) and all(
        counts.get(name, -1) == (1 if name in ONE_LAWS else 0)
        for name in law_order)


def translate_java_unicode_escapes(source: str) -> tuple[str, tuple[str, ...]]:
    """Apply Java's raw-input Unicode translation before lexical analysis.

    A raw backslash is eligible only after an even-length translated backslash
    run. This preserves ordinary Java spellings such as ``"\\\\u001f"`` while
    still decoding the repeated-u form (``\\uuuu0041``). An eligible ``\\u``
    prefix without exactly four hexadecimal digits is an invalid Java Unicode
    escape and therefore a fail-closed input error.
    """
    translated: list[str] = []
    errors: list[str] = []
    index = 0
    while index < len(source):
        char = source[index]
        if char != "\\":
            translated.append(char)
            index += 1
            continue
        preceding_backslashes = 0
        for previous in reversed(translated):
            if previous != "\\":
                break
            preceding_backslashes += 1
        eligible = preceding_backslashes % 2 == 0
        if not eligible or index + 1 >= len(source) or source[index + 1] != "u":
            translated.append(char)
            index += 1
            continue
        marker_end = index + 1
        while marker_end < len(source) and source[marker_end] == "u":
            marker_end += 1
        digits = source[marker_end:marker_end + 4]
        if len(digits) != 4 or not re.fullmatch(r"[0-9a-fA-F]{4}", digits):
            errors.append(f"invalid Java Unicode escape at offset {index}")
            translated.append(" ")
            index = marker_end
            continue
        translated.append(chr(int(digits, 16)))
        index = marker_end + 4
    return "".join(translated), tuple(errors)


def strip_java_comments(source: str) -> str:
    """Strip comments while retaining strings, text blocks, and line positions."""
    def retain_literal(match: re.Match[str]) -> str:
        if match.group(1) or match.group(2) or match.group(3):
            return match.group()
        return "".join("\n" if char == "\n" else " " for char in match.group())

    return JAVA_COMMENT_OR_LITERAL.sub(retain_literal, source)


@lru_cache(maxsize=None)
def normalized_java_source(source: str) -> tuple[str, tuple[str, ...]]:
    translated, errors = translate_java_unicode_escapes(source)
    return strip_java_comments(translated), errors


def strip_sql_comments(source: str) -> str:
    return re.sub(r"--[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", source, flags=re.DOTALL))


def mask_literals(source: str) -> str:
    return re.sub(r'\"(?:\\.|[^\"\\])*\"|\'(?:\\.|[^\'\\])*\'',
                  lambda match: " " * len(match.group()), source, flags=re.DOTALL)


def method_body(source: str, method_name: str) -> str:
    structural = mask_literals(source)
    declaration = re.compile(
        r"(?m)^[ \t]*(?:(?:public|protected|private)\s+(?:static\s+)?[\w<>.?]+|"
        r"(?:static\s+)?(?:[A-Z_$][\w<>.?]*|void))\s+"
        + re.escape(method_name) + r"\s*\(")
    matches = list(declaration.finditer(structural))
    if len(matches) != 1:
        return ""
    opening = structural.find("{", matches[0].end())
    if opening < 0:
        return ""
    depth = 0
    for index in range(opening, len(structural)):
        if structural[index] == "{":
            depth += 1
        elif structural[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    return ""


def first_executable_top_level_statement(body: str) -> str:
    """Return the first non-empty top-level Java statement.

    A leading control-flow block, lambda, conditional, or local declaration is
    deliberately returned together with its terminating semicolon. It therefore
    cannot masquerade as the required direct authorization call.
    """
    structural = mask_literals(body)
    start = 0
    while start < len(structural):
        if structural[start].isspace() or structural[start] == ";":
            start += 1
            continue
        break
    parentheses = 0
    brackets = 0
    braces = 0
    for index in range(start, len(structural)):
        char = structural[index]
        if char == "(":
            parentheses += 1
        elif char == ")":
            parentheses -= 1
        elif char == "[":
            brackets += 1
        elif char == "]":
            brackets -= 1
        elif char == "{":
            braces += 1
        elif char == "}":
            braces -= 1
        elif char == ";" and parentheses == brackets == braces == 0:
            return body[start:index + 1].strip()
    return body[start:].strip()


def has_required_preparation_authorization_first(body: str) -> bool:
    statement = first_executable_top_level_statement(body)
    return bool(DIRECT_PREPARATION_AUTHORIZATION.fullmatch(mask_literals(statement)))


def brace_depth_at(source: str, position: int) -> int:
    structural = mask_literals(source[:position])
    return structural.count("{") - structural.count("}")


def matching_delimiter(source: str, opening: int, opener: str, closer: str) -> int:
    """Return the matching delimiter position in already masked Java source."""
    if opening < 0 or opening >= len(source) or source[opening] != opener:
        return -1
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == opener:
            depth += 1
        elif source[index] == closer:
            depth -= 1
            if depth == 0:
                return index
    return -1


def top_level_if_blocks(source: str) -> tuple[tuple[str, str, int], ...]:
    """Extract direct if blocks; nested/dead/try-wrapped checks are excluded."""
    structural = mask_literals(source)
    blocks: list[tuple[str, str, int]] = []
    search_from = 0
    while (match := re.search(r"\bif\s*\(", structural[search_from:])) is not None:
        start = search_from + match.start()
        opening_condition = structural.find("(", start)
        closing_condition = matching_delimiter(
            structural, opening_condition, "(", ")")
        if closing_condition < 0:
            break
        opening_body = closing_condition + 1
        while opening_body < len(structural) and structural[opening_body].isspace():
            opening_body += 1
        if opening_body >= len(structural) or structural[opening_body] != "{":
            search_from = closing_condition + 1
            continue
        closing_body = matching_delimiter(structural, opening_body, "{", "}")
        if closing_body < 0:
            break
        if brace_depth_at(structural, start) == 0:
            blocks.append((
                source[opening_condition + 1:closing_condition],
                source[opening_body + 1:closing_body],
                start))
        search_from = closing_body + 1
    return tuple(blocks)


def named_direct_block(source: str, declaration: str) -> str:
    """Return a uniquely named direct block, such as a compact constructor."""
    structural = mask_literals(source)
    matches = list(re.finditer(declaration + r"\s*\{", structural))
    if len(matches) != 1:
        return ""
    opening = structural.find("{", matches[0].start())
    closing = matching_delimiter(structural, opening, "{", "}")
    return source[opening + 1:closing] if closing >= 0 else ""


def exact_throw_statement(body: str, required_expression: str | None = None) -> bool:
    """Prove that a branch consists of one unconditional throw statement."""
    structural = mask_literals(body).strip()
    if not re.fullmatch(r"throw\s+[^;]+;", structural, flags=re.DOTALL):
        return False
    return required_expression is None or bool(re.search(
        required_expression, body, flags=re.MULTILINE | re.DOTALL))


def normalized_expression(expression: str) -> str:
    return re.sub(r"\s+", "", mask_literals(expression))


def exact_base_hash_fail_closed(operation_request: str) -> bool:
    """Require null and blank base hashes to reach a direct rejection."""
    constructor = named_direct_block(
        operation_request, r"\bpublic\s+OperationRequest")
    if not constructor or re.search(
            r"\bbaseContentHash\s*(?<![=!<>])=(?!=)|"
            r"\b(?:default|fallback)[A-Za-z0-9_$]*\s*\(",
            mask_literals(constructor), flags=re.IGNORECASE):
        return False
    null_predicates = {
        "baseContentHash==null", "null==baseContentHash",
        "Objects.isNull(baseContentHash)",
    }
    blank_predicates = {
        "baseContentHash.isBlank()", "baseContentHash.trim().isEmpty()",
    }
    null_rejected = False
    blank_rejected = False
    for condition, body, _ in top_level_if_blocks(constructor):
        if not exact_throw_statement(body):
            continue
        normalized = normalized_expression(condition)
        if normalized in null_predicates:
            null_rejected = True
        if normalized in blank_predicates:
            blank_rejected = True
        for null_predicate in null_predicates:
            for blank_predicate in blank_predicates:
                if normalized in {
                        null_predicate + "||" + blank_predicate,
                        blank_predicate + "||" + null_predicate}:
                    null_rejected = True
                    blank_rejected = True
    # Objects.requireNonNull is a mechanically known null rejection and may be
    # paired with a separate direct blank rejection.
    if re.search(
            r"(?m)^\s*Objects\s*\.\s*requireNonNull\s*\(\s*baseContentHash\b[^;]*;",
            mask_literals(constructor)):
        null_rejected = True
    return null_rejected and blank_rejected


def exact_unsupported_operation_guard(invoke_body: str, delegate: str) -> bool:
    """Bind either exact identity mismatch to the typed rejection before dispatch."""
    id_mismatch = (
        "!OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId()"
        ".equals(request.definitionId())")
    version_mismatch = (
        "!OperationDefinition.V1.ADD_MEDIA_CLIP.version()"
        ".equals(request.version())")
    delegate_position = invoke_body.find(delegate)
    if delegate_position < 0:
        return False
    typed_throw = (
        r"throw\s+failure\s*\(\s*OperationInvocationFailureCode\s*\.\s*"
        r"UNSUPPORTED_OPERATION\s*,\s*\"unsupported-operation\"\s*\)\s*;")
    for condition, body, position in top_level_if_blocks(invoke_body):
        normalized = normalized_expression(condition)
        if (normalized in {
                id_mismatch + "||" + version_mismatch,
                version_mismatch + "||" + id_mismatch}
                and position < delegate_position
                and exact_throw_statement(body, typed_throw)):
            return True
    return False


def exact_authorization_decision_binding(execute_body: str) -> bool:
    """Prove securityDecision.allowed() selects allow vs deny before apply."""
    structural = mask_literals(execute_body)
    # The one declaration assignment must remain the only binding. Replacing
    # the port result through a later reassignment invalidates authorization.
    if occurrences(r"\bsecurityDecision\s*=(?!=)", structural) != 1:
        return False
    ternary = re.search(
        r"\bAuthorizationDecision\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
        r"securityDecision\s*\.\s*allowed\s*\(\s*\)\s*\?\s*"
        r"AuthorizationDecision\s*\.\s*allow\s*\([^;]*\)\s*:\s*"
        r"AuthorizationDecision\s*\.\s*deny\s*\([^;]*\)\s*;",
        structural, flags=re.DOTALL)
    if ternary and brace_depth_at(structural, ternary.start()) != 0:
        ternary = None
    bound_name = ternary.group("name") if ternary else ""
    binding_end = ternary.end() if ternary else -1
    if not ternary:
        if_binding = re.search(
            r"\bAuthorizationDecision\s+(?P<if_name>[A-Za-z_$][A-Za-z0-9_$]*)\s*;\s*"
            r"if\s*\(\s*securityDecision\s*\.\s*allowed\s*\(\s*\)\s*\)\s*\{\s*"
            r"(?P=if_name)\s*=\s*AuthorizationDecision\s*\.\s*allow\s*\([^;]*\)\s*;\s*"
            r"\}\s*else\s*\{\s*(?P=if_name)\s*=\s*"
            r"AuthorizationDecision\s*\.\s*deny\s*\([^;]*\)\s*;\s*\}",
            structural, flags=re.DOTALL)
        if if_binding and brace_depth_at(structural, if_binding.start()) == 0:
            bound_name = if_binding.group("if_name")
            binding_end = if_binding.end()
    if not bound_name:
        return False
    apply_context = re.search(
        r"\bApplyContext\s+[A-Za-z_$][A-Za-z0-9_$]*\s*=\s*new\s+ApplyContext\s*"
        r"\([^;]*\b" + re.escape(bound_name) + r"\b[^;]*\)\s*;",
        structural[binding_end:], flags=re.DOTALL)
    apply_call = structural.find("applyService.apply", binding_end)
    return apply_context is not None and apply_call > binding_end + apply_context.end()


def authorization_helper_is_narrow(body: str) -> bool:
    """Keep the pre-hydration helper limited to identity checks and auth I/O."""
    structural = mask_literals(body)
    if not structural.strip() or PREPARATION_MECHANIC.search(structural):
        return False
    calls = {
        re.sub(r"\s+", "", match.group(1))
        for match in JAVA_CALL.finditer(structural)
        if match.group(1) not in {"if", "for", "while", "switch", "catch", "synchronized"}
    }
    if not calls.issubset(AUTHORIZATION_HELPER_ALLOWED_CALLS):
        return False
    direct_decision = re.search(
        r"\bvar\s+decision\s*=\s*authorizationPort\s*\.\s*decide\s*\(",
        structural)
    return (
        direct_decision is not None
        and brace_depth_at(structural, direct_decision.start()) == 0
        and occurrences(r"\bauthorizationPort\s*\.\s*decide\s*\(", structural) == 1
        and occurrences(r"\bdecision\s*\.\s*allowed\s*\(", structural) == 1)


def ordered(source: str, tokens: tuple[str, ...]) -> bool:
    position = -1
    for token in tokens:
        position = source.find(token, position + 1)
        if position < 0:
            return False
    return True


def is_exact_plan_changed_throw(body: str) -> bool:
    """Require one typed throw statement and no fall-through statement."""
    structural = mask_literals(body).strip()
    prefix = re.match(
        r"throw\s+new\s+TimelineOperationException\s*\(\s*"
        r"TimelineOperationException\s*\.\s*Code\s*\.\s*PLAN_CHANGED\s*,",
        structural)
    if prefix is None:
        return False
    opening = structural.find("(")
    depth = 0
    for index in range(opening, len(structural)):
        if structural[index] == "(":
            depth += 1
        elif structural[index] == ")":
            depth -= 1
            if depth == 0:
                return structural[index + 1:].strip() == ";"
    return False


def is_exact_timeline_throw(body: str, code: str) -> bool:
    """Require one unconditional typed TimelineOperationException throw."""
    structural = mask_literals(body).strip()
    prefix = re.match(
        r"throw\s+new\s+TimelineOperationException\s*\(\s*"
        r"TimelineOperationException\s*\.\s*Code\s*\.\s*"
        + re.escape(code) + r"\s*,",
        structural)
    if prefix is None:
        return False
    opening = structural.find("(")
    depth = 0
    for index in range(opening, len(structural)):
        if structural[index] == "(":
            depth += 1
        elif structural[index] == ")":
            depth -= 1
            if depth == 0:
                return structural[index + 1:].strip() == ";"
    return False


def has_exact_leading_digest_guard(body: str) -> bool:
    """Bind the digest mismatch throw to the first post-actor control."""
    structural = mask_literals(body)
    leading = re.match(
        r"\s*Objects\s*\.\s*requireNonNull\s*\(\s*actor\s*,[^;]*\)\s*;\s*"
        r"if\s*\(\s*!\s*prepared\s*\.\s*plan\s*\(\s*\)\s*\.\s*planDigest\s*"
        r"\(\s*\)\s*\.\s*equals\s*\(\s*expectedPlanDigest\s*\)\s*\)\s*"
        r"\{(?P<throw>[^{}]*)\}",
        structural, flags=re.DOTALL)
    if leading is None or not is_exact_plan_changed_throw(leading.group("throw")):
        return False
    if occurrences(r"TimelineOperationException\s*\.\s*Code\s*\.\s*PLAN_CHANGED\b",
                   structural) != 1:
        return False
    # A broad catch is never a permitted PLAN_CHANGED recovery mechanism.
    return not re.search(
        r"\bcatch\s*\(\s*(?:final\s+)?(?:java\s*\.\s*lang\s*\.\s*)?"
        r"(?:Throwable|Exception|RuntimeException|TimelineOperationException)\b",
        structural)


def authorization_helper_fails_closed(body: str) -> bool:
    """Require the exact identity-check -> decision -> denial-throw shape."""
    structural = mask_literals(body)
    if (not authorization_helper_is_narrow(body)
            or re.search(r"\b(?:try|catch|finally|return)\b", structural)):
        return False
    shape = re.fullmatch(
        r"\s*Objects\s*\.\s*requireNonNull\s*\(\s*actor\s*,[^;]*\)\s*;\s*"
        r"if\s*\(\s*!\s*Objects\s*\.\s*equals\s*\(\s*tenantId\s*,\s*"
        r"actor\s*\.\s*tenantId\s*\(\s*\)\s*\)\s*\)\s*"
        r"\{(?P<tenant>[^{}]*)\}\s*"
        r"var\s+decision\s*=\s*authorizationPort\s*\.\s*decide\s*\(.*?\)\s*;\s*"
        r"if\s*\(\s*!\s*decision\s*\.\s*allowed\s*\(\s*\)\s*\)\s*"
        r"\{(?P<denied>[^{}]*)\}\s*",
        structural, flags=re.DOTALL)
    return bool(
        shape
        and is_exact_timeline_throw(shape.group("tenant"), "TENANT_CONTEXT_MISMATCH")
        and is_exact_timeline_throw(shape.group("denied"), "AUTHORIZATION_DENIED"))


def raw_failure_inference_occurrences(source: str) -> int:
    """Count message/text-derived classification from every throwable catch type."""
    count = occurrences(
        r"(?:getMessage\s*\(\s*\)|\b(?:message|reason|detail)\b)\s*"
        r"\.\s*(?:contains|startsWith|endsWith|matches|equalsIgnoreCase)\s*\(|"
        r"switch\s*\([^)]*(?:getMessage\s*\(\s*\)|\bmessage\b)[^)]*\)|"
        r"OperationInvocationFailureCode\s*\.\s*valueOf\s*\(",
        source)
    caught: set[str] = set()
    catch_declaration = re.compile(
        r"\bcatch\s*\(\s*(?:final\s+)?(?P<types>"
        r"[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*\|\s*"
        r"[A-Za-z_$][A-Za-z0-9_$.]*)*)\s+"
        r"(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\)")
    for catch in catch_declaration.finditer(source):
        types = re.split(r"\s*\|\s*", catch.group("types"))
        if any(re.search(r"(?:Exception|Throwable|Error)$", item) for item in types):
            caught.add(catch.group("name"))

    assignments = tuple(re.finditer(
        r"\b(?:String|var)\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
        r"(?P<expression>[^;]+)\s*;", source, flags=re.DOTALL))
    for failure in caught:
        name = re.escape(failure)
        conversion = (
            rf"(?:{name}\s*\.\s*toString\s*\(\s*\)|"
            rf"String\s*\.\s*valueOf\s*\(\s*{name}\s*\)|"
            rf"(?:String|MessageFormat)\s*\.\s*format\s*\([^;]*\b{name}\b[^;]*\)|"
            rf"[^;]*\.\s*formatted\s*\([^;]*\b{name}\b[^;]*\)|"
            rf"(?:\"[^\"]*\"\s*\+\s*{name}|{name}\s*\+\s*\"[^\"]*\")|"
            rf"(?:STR\s*\.\s*)?\"[^\"]*(?:\\\{{|\$\{{)\s*{name}\b[^\"]*\")")
        count += occurrences(
            conversion
            + r"\s*\.\s*(?:contains|startsWith|endsWith|matches|equals|"
              r"equalsIgnoreCase)\s*\(",
            source)
        count += occurrences(
            r"(?:\"[^\"]*\"|[A-Za-z_$][A-Za-z0-9_$.]*)\s*\.\s*"
            r"(?:contains|startsWith|endsWith|matches|equals|equalsIgnoreCase)\s*"
            r"\([^;)]*" + conversion,
            source)
        count += occurrences(r"switch\s*\(\s*" + conversion, source)
        count += occurrences(
            r"\b[A-Za-z_$][A-Za-z0-9_$.]*(?:Code|Failure|Error|Enum)\s*\.\s*"
            r"valueOf\s*\([^;]*" + conversion,
            source)
        tainted: set[str] = set()
        changed = True
        while changed:
            changed = False
            for assignment in assignments:
                variable = assignment.group("name")
                expression = assignment.group("expression")
                carries_failure_text = bool(re.search(conversion, expression, flags=re.DOTALL))
                carries_existing_taint = any(re.search(
                    r"\b" + re.escape(value) + r"\b", expression)
                    for value in tainted)
                if (carries_failure_text or carries_existing_taint) and variable not in tainted:
                    tainted.add(variable)
                    changed = True
        for variable in tainted:
            value = re.escape(variable)
            count += occurrences(
                rf"\b{value}\s*\.\s*(?:contains|startsWith|endsWith|matches|equals|"
                rf"equalsIgnoreCase)\s*\(|switch\s*\(\s*{value}\s*\)|"
                rf"(?:[A-Za-z_$][A-Za-z0-9_$.]*(?:Code|Failure|Error)|Enum)\s*"
                rf"\.\s*valueOf\s*"
                rf"\([^;)]*\b{value}\b",
                source)
    return count


def occurrences(pattern: str, source: str) -> int:
    return len(re.findall(pattern, source, flags=re.MULTILINE | re.DOTALL))


def governed_runtime_hash_mismatches(
        sources: dict[str, str]) -> tuple[int, tuple[str, ...]]:
    """Attest the exact frozen runtime universe before semantic decoding."""
    mismatch_count = abs(
        len(GOVERNED_RUNTIME_SOURCE_SHA256)
        - REQUIRED_GOVERNED_RUNTIME_SOURCE_COUNT)
    details: list[str] = []
    if mismatch_count:
        details.append(
            "governed runtime hash census expected "
            f"{REQUIRED_GOVERNED_RUNTIME_SOURCE_COUNT}, found "
            f"{len(GOVERNED_RUNTIME_SOURCE_SHA256)}")
    for path, expected_hash in GOVERNED_RUNTIME_SOURCE_SHA256.items():
        if (Path(path).is_absolute() or Path(path).as_posix() != path
                or ".." in Path(path).parts
                or re.fullmatch(r"[0-9a-f]{64}", expected_hash) is None):
            mismatch_count += 1
            details.append(f"invalid governed runtime hash manifest entry: {path}")
            continue
        source = sources.get(path)
        if source is None:
            mismatch_count += 1
            details.append(f"governed runtime source missing: {path}")
            continue
        actual_hash = hashlib.sha256(source.encode("utf-8")).hexdigest()
        if actual_hash != expected_hash:
            mismatch_count += 1
            details.append(
                f"governed runtime source hash mismatch: {path}: {actual_hash}")
    return mismatch_count, tuple(details)


def historical_h8_changed_paths(
        root: Path,
        pre_canonical_base_sha: str = H8_PRE_CANONICAL_BASE_SHA,
        accepted_canonical_sha: str = H8_ACCEPTED_CANONICAL_SHA,
        current_head: str = "HEAD") -> tuple[set[str], list[str], bool]:
    """Attest the immutable accepted H8 delta and the current checkout lineage."""
    errors: list[str] = []
    changed: set[str] = set()
    commits_exist = True
    for label, commit in (
            ("pre-canonical base", pre_canonical_base_sha),
            ("accepted canonical", accepted_canonical_sha)):
        command = ("git", "cat-file", "-e", f"{commit}^{{commit}}")
        try:
            completed = subprocess.run(
                command, cwd=root, check=False, capture_output=True, timeout=30)
        except (OSError, subprocess.SubprocessError) as failure:
            errors.append(f"cannot verify H8 {label} commit {commit}: {failure}")
            commits_exist = False
            continue
        if completed.returncode != 0:
            errors.append(f"H8 {label} commit does not exist: {commit}")
            commits_exist = False
    if not commits_exist:
        return changed, errors, False

    ancestry_checks = (
        (pre_canonical_base_sha, accepted_canonical_sha,
         "H8 pre-canonical base is not an ancestor of accepted canonical checkpoint"),
        (accepted_canonical_sha, current_head,
         "current HEAD is not descended from accepted H8 canonical checkpoint"),
    )
    current_head_descendant = False
    for ancestor, descendant, not_ancestor_error in ancestry_checks:
        command = ("git", "merge-base", "--is-ancestor", ancestor, descendant)
        try:
            completed = subprocess.run(
                command, cwd=root, check=False, capture_output=True, timeout=30)
        except (OSError, subprocess.SubprocessError) as failure:
            errors.append(
                f"cannot verify H8 ancestry with {' '.join(command)}: {failure}")
            continue
        if completed.returncode == 0:
            if ancestor == accepted_canonical_sha and descendant == current_head:
                current_head_descendant = True
        elif completed.returncode == 1:
            errors.append(
                f"{not_ancestor_error}: {ancestor} !<= {descendant}")
        else:
            errors.append(
                f"cannot verify H8 ancestry with {' '.join(command)}: "
                f"exit {completed.returncode}")

    command = (
        "git", "diff", "--name-only", "--no-renames", "-z",
        pre_canonical_base_sha, accepted_canonical_sha, "--")
    try:
        completed = subprocess.run(
            command, cwd=root, check=False, capture_output=True, timeout=30)
    except (OSError, subprocess.SubprocessError) as failure:
        errors.append(
            f"cannot inspect historical H8 changes with {' '.join(command)}: {failure}")
        return changed, errors, current_head_descendant
    if completed.returncode != 0:
        errors.append(
            f"cannot inspect historical H8 changes with {' '.join(command)}: "
            f"exit {completed.returncode}")
        return changed, errors, current_head_descendant
    for encoded_path in completed.stdout.split(b"\0"):
        if not encoded_path:
            continue
        try:
            changed.add(encoded_path.decode("utf-8"))
        except UnicodeDecodeError as failure:
            errors.append(f"cannot decode historical H8 Git path as UTF-8: {failure}")
    return changed, errors, current_head_descendant


def sources_at(root: Path) -> tuple[dict[str, str], list[str], set[str], bool]:
    errors: list[str] = []
    if not root.is_dir():
        return {}, [f"repository root is missing: {root}"], set(), False
    sources: dict[str, str] = {}
    candidates: list[tuple[Path, str]] = []
    for path in root.rglob("*.java"):
        relative = path.relative_to(root).as_posix()
        lowered = relative.lower()
        if "/src/main/java/" in f"/{relative}" or (
                "jooq" in lowered and "generated" in lowered):
            candidates.append((path, "java"))
    for path in root.rglob("*.sql"):
        relative = path.relative_to(root).as_posix()
        lowered = relative.lower()
        if "migration" in lowered or "schema" in lowered:
            candidates.append((path, "sql"))
    for path, source_type in candidates:
        relative = path.relative_to(root).as_posix()
        try:
            # Decode explicit bytes without universal-newline translation so
            # SHA-256 attestation observes the exact repository file bytes.
            raw = path.read_bytes().decode("utf-8")
            # Java must remain raw until evaluate(), where Unicode translation
            # precedes comment/literal analysis for both repository and mutated
            # self-test sources.
            sources[relative] = raw if source_type == "java" else strip_sql_comments(raw)
        except (OSError, UnicodeError) as failure:
            errors.append(f"cannot read {relative}: {failure}")
    governed_roots = ("operation-module/", "render-module/", "workflow-module/", "timeline-module/")
    for governed in governed_roots:
        if not any(path.startswith(governed) for path in sources):
            errors.append(f"empty governed production scope: {governed}")
    if not sources:
        errors.append("production Java census is empty")
    changed_paths, change_errors, current_head_descendant = (
        historical_h8_changed_paths(root))
    errors.extend(change_errors)
    return sources, errors, changed_paths, current_head_descendant


def source_ending(sources: dict[str, str], suffix: str, details: list[str]) -> str:
    matches = [(path, source) for path, source in sources.items() if path.endswith(suffix)]
    if len(matches) != 1:
        details.append(f"expected one {suffix}, found {len(matches)}")
        return ""
    return matches[0][1]


def declarations(
        sources: dict[str, str], pattern: str, hint: str, path_prefix: str = "") -> int:
    compiled = re.compile(pattern, flags=re.MULTILINE | re.DOTALL)
    return sum(len(compiled.findall(source)) for path, source in sources.items()
               if path.startswith(path_prefix) and hint in source)


@lru_cache(maxsize=None)
def source_metadata(source: str) -> tuple[str, tuple[str, ...], frozenset[str], frozenset[str]]:
    package_match = PACKAGE.search(source)
    package_name = package_match.group(1) if package_match else ""
    names = tuple(TYPE_DECLARATION.findall(source))
    imports = frozenset(
        item.removeprefix("static ").strip()
        for item in IMPORT.findall(source)
    )
    words = frozenset(JAVA_WORD.findall(source))
    return package_name, names, imports, words


@lru_cache(maxsize=None)
def qualified_names(source: str) -> tuple[str, ...]:
    return tuple(JAVA_QUALIFIED_NAME.findall(source))


def primary_type(source: str) -> str:
    names = source_metadata(source)[1]
    return names[0] if names else ""


def declared_data_identifiers(source: str) -> set[str]:
    """Collect record components, fields, parameters, and interface accessors."""
    structural = mask_literals(source)
    identifiers = set(re.findall(
        r"\b[A-Za-z_$][A-Za-z0-9_$.<>?\[\],]*\s+"
        r"([A-Za-z_$][A-Za-z0-9_$]*)\s*(?=[,;)=])",
        structural))
    identifiers.update(re.findall(
        r"\b[A-Za-z_$][A-Za-z0-9_$.<>?\[\],]*\s+"
        r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)\s*;",
        structural))
    return {re.sub(r"[^a-z0-9]", "", item.lower()) for item in identifiers}


def duplicates_operation_request_semantics(path: str, source: str) -> bool:
    """Identify a structural invocation-request peer without banning other DTOs."""
    if path.endswith("operation/operation/OperationRequest.java"):
        return False
    names = source_metadata(source)[1]
    workflow_owned = path.startswith("workflow-module/src/main/java/")
    # Operation's own same-named peer is an explicit forbidden shadow. A
    # Workflow DTO called OperationRequest is not: Workflow ownership requires
    # either a known peer name or the complete semantic intent field set.
    if (path.startswith("operation-module/src/main/java/")
            and "OperationRequest" in names):
        return True
    if workflow_owned and any(name in {
            "WorkflowOperationRequest", "CanonicalOperationRequest",
            "OperationInvocationRequest",
    } for name in names):
        return True
    peer_named = any(re.search(
        r"(?i)(?:workflow|invocation|request|intent|carrier|envelope|command)",
        name) for name in names)
    if not names or not (workflow_owned or peer_named):
        return False
    identifiers = declared_data_identifiers(source)

    def has(pattern: str) -> bool:
        return any(re.fullmatch(pattern, identifier) for identifier in identifiers)

    return all((
        has(r".*definitionid"),
        has(r"(?:definition)?version|.*definitionversion"),
        has(r".*target(?:request|ref|id)?"),
        has(r".*(?:parameters|params)"),
        has(r"base.*revision(?:id|ref)?"),
        has(r"base.*(?:content)?hash"),
    ))


def declaration_data_identifiers(
        source: str, declaration: re.Match[str]) -> set[str]:
    """Collect one type's header and direct field/accessor surface only."""
    structural = mask_literals(source)
    opening = structural.find("{", declaration.end())
    if opening < 0:
        return set()
    try:
        closing = matching_delimiter(structural, opening, "{", "}")
    except ValueError:
        closing = len(structural)
    direct_body: list[str] = []
    depth = 0
    for index in range(opening + 1, closing):
        source_char = source[index]
        structural_char = structural[index]
        if structural_char == "{":
            depth += 1
            direct_body.append(" ")
        elif structural_char == "}":
            depth = max(0, depth - 1)
            direct_body.append(" ")
        else:
            direct_body.append(source_char if depth == 0 else " ")
    return declared_data_identifiers(
        source[declaration.start():opening] + "\n" + "".join(direct_body))


def request_controlled_actor_identifiers(source: str) -> int:
    """Detect normalized actor identity fields in request semantic intent only."""
    identifiers = declared_data_identifiers(source)
    return sum(
        identifier in {"actor", "principal", "useridentity", "subject"}
        or bool(re.fullmatch(
            r"(?:.*(?:actor|principal|user|subject).*(?:id|ref)|"
            r"(?:actor|principal|user|subject)(?:id|ref).*)",
            identifier))
        for identifier in identifiers)


@lru_cache(maxsize=None)
def invocation_semantic_authorities(
        path: str, source: str, workflow_owned: bool,
        called_runtime_helper: bool) -> tuple[set[str], set[str]]:
    """Detect named or structural peer result/failure contract authority."""
    package_name, names, _, _ = source_metadata(source)
    owner = names[0] if names else ""
    owner_fq = f"{package_name}.{owner}" if package_name and owner else ""
    result_authorities: set[str] = set()
    failure_authorities: set[str] = set()
    if (owner_fq == CANONICAL_INVOCATION_RESULT_OWNER
            or owner_fq in CANONICAL_INVOCATION_FAILURE_OWNERS):
        return result_authorities, failure_authorities
    structural = mask_literals(source)
    for declaration in TYPE_DECLARATION_WITH_MODIFIERS.finditer(structural):
        name = declaration.group("name")
        modifiers = set(declaration.group("modifiers").split())
        nesting_depth = (
            structural.count("{", 0, declaration.start())
            - structural.count("}", 0, declaration.start()))
        package_visible = not modifiers.intersection({"private", "protected"})
        workflow_contract = workflow_owned and package_visible
        runtime_authority = called_runtime_helper and (
            "public" in modifiers or nesting_depth > 0)
        allowed_internal_outcome = (
            owner_fq
            == "com.example.platform.render.app.operation.TimelineMediaClipOperationService"
            and name == "InvocationOutcome"
            and nesting_depth > 0
            and not modifiers.intersection({"public", "protected", "private"}))
        if not (workflow_contract or runtime_authority) or allowed_internal_outcome:
            continue
        identifiers = declaration_data_identifiers(source, declaration)

        def has(pattern: str) -> bool:
            return any(re.fullmatch(pattern, identifier) for identifier in identifiers)

        definition_identity = (
            has(r"(?:operation)?definitionid|.*operationdefinitionid")
            and has(r"(?:operation)?definitionversion|definitionversion|version"))
        invocation_identity = has(
            r"(?:operation)?invocationid|workflowcallid|callid|invocationidentity")
        structural_result = all((
            definition_identity,
            has(r"(?:operation)?plandigest|plandigest"),
            has(r"(?:base|new)?revision(?:id)?|(?:new)?contenthash|status|success|successful"),
        ))
        structural_failure = all((
            definition_identity or invocation_identity,
            has(r"failurecode|errorcode|denialcode|code"),
            has(r"reason|message|explanation"),
            has(r"retry(?:able|after|delay|millis|seconds)?|backoff(?:millis|seconds)?"),
        ))
        authority = f"{path}:{name}"
        if name.startswith("Invocation"):
            # Invocation-prefixed peers remain conservatively bound to both
            # polarities even if they invent new result/failure vocabulary.
            result_authorities.add(authority)
            failure_authorities.add(authority)
        else:
            if structural_result:
                result_authorities.add(authority)
            if structural_failure:
                failure_authorities.add(authority)
    return result_authorities, failure_authorities


def sources_declaring_type(
        sources: dict[str, str], type_name: str) -> list[tuple[str, str]]:
    declaration = re.compile(
        r"\b(?:public\s+)?(?:final\s+|sealed\s+|non-sealed\s+|abstract\s+)*"
        r"(?:class|interface|record|enum)\s+" + re.escape(type_name) + r"\b")
    return [(path, source) for path, source in sources.items()
            if path.endswith(".java") and declaration.search(source)]


def sources_declaring_fq_type(
        sources: dict[str, str], fq_type_name: str) -> list[tuple[str, str]]:
    package_name, type_name = fq_type_name.rsplit(".", 1)
    return [
        (path, source) for path, source in sources_declaring_type(sources, type_name)
        if (match := PACKAGE.search(source)) and match.group(1) == package_name
    ]


def one_source_declaring_type(
        sources: dict[str, str], type_name: str, details: list[str]) -> tuple[str, str]:
    matches = sources_declaring_type(sources, type_name)
    if len(matches) != 1:
        details.append(f"expected one production type {type_name}, found {len(matches)}")
        return "", ""
    return matches[0]


def production_type_graph(
        java_sources: dict[str, str], start_paths: set[str]
        ) -> tuple[dict[str, str], dict[str, set[str]], dict[str, set[str]]]:
    """Resolve exact, wildcard, same-package, and fully-qualified type references."""
    fq_type_paths: dict[str, str] = {}
    metadata: dict[str, tuple[str, set[str], set[str]]] = {}
    for path, source in java_sources.items():
        package_name, names, imports, words = source_metadata(source)
        declared = names[0] if names else ""
        if not package_name or not declared:
            continue
        fq_type_paths[f"{package_name}.{declared}"] = path
        metadata[path] = (package_name, set(imports), set(words))

    def resolve_qualified_name(name: str) -> str | None:
        candidate = name
        while "." in candidate:
            dependency = fq_type_paths.get(candidate)
            if dependency:
                return dependency
            candidate = candidate.rsplit(".", 1)[0]
        return None

    explicit_fq_references: dict[str, set[str]] = {
        path: {
            dependency
            for name in qualified_names(source)
            if (dependency := resolve_qualified_name(name)) is not None
        }
        for path, source in java_sources.items()
    }

    @lru_cache(maxsize=None)
    def resolve_references(path: str) -> frozenset[str]:
        if path not in metadata:
            return frozenset()
        package_name, imports, words = metadata[path]
        resolved: set[str] = set()
        for imported in imports:
            dependency = fq_type_paths.get(imported)
            if dependency:
                resolved.add(dependency)
            if imported.endswith(".*"):
                imported_package = imported[:-2]
                for word in words:
                    dependency = fq_type_paths.get(f"{imported_package}.{word}")
                    if dependency:
                        resolved.add(dependency)
            imported_dependency = resolve_qualified_name(imported)
            if imported_dependency:
                resolved.add(imported_dependency)
        for word in words:
            dependency = fq_type_paths.get(f"{package_name}.{word}")
            if dependency:
                resolved.add(dependency)
        resolved.update(explicit_fq_references.get(path, set()))
        return frozenset(resolved)

    dependencies: dict[str, set[str]] = {}
    pending = list(start_paths)
    visited: set[str] = set()
    while pending:
        path = pending.pop()
        if path in visited:
            continue
        visited.add(path)
        resolved = set(resolve_references(path))
        dependencies[path] = resolved
        pending.extend(resolved - visited)

    cross_package_references: dict[str, set[str]] = {}
    reference_paths = visited | {
        path for path in java_sources
        if path.startswith("workflow-module/src/main/java/")
    }
    for path in reference_paths:
        source = java_sources.get(path, "")
        package_name = source_metadata(source)[0]
        cross_package_references[path] = {
            dependency_path
            for dependency_path in resolve_references(path)
            if not source_metadata(java_sources[dependency_path])[0] == package_name
        }
    return fq_type_paths, dependencies, cross_package_references


def reachable_paths(start_paths: set[str], dependencies: dict[str, set[str]]) -> set[str]:
    reached = set(start_paths)
    pending = list(start_paths)
    while pending:
        current = pending.pop()
        for dependency in dependencies.get(current, set()):
            if dependency not in reached:
                reached.add(dependency)
                pending.append(dependency)
    return reached


def evaluate(
        sources: dict[str, str], input_errors: list[str] | None = None,
        changed_paths: set[str] | None = None) -> Evaluation:
    details = list(input_errors or [])
    changed_paths = set(changed_paths or ())
    changed_path_scope_violations = len(
        changed_paths.symmetric_difference(H8_AUTHORIZED_CHANGED_PATHS))
    governed_hash_mismatches, governed_hash_details = (
        governed_runtime_hash_mismatches(sources))
    details.extend(governed_hash_details)
    normalized_sources: dict[str, str] = {}
    for path, source in sources.items():
        if not path.endswith(".java"):
            normalized_sources[path] = source
            continue
        normalized_sources[path], unicode_errors = normalized_java_source(source)
        details.extend(f"{path}: {error}" for error in unicode_errors)
    sources = normalized_sources
    java_sources = {
        path: source for path, source in sources.items() if path.endswith(".java")
    }
    declared_type_sources: dict[str, list[tuple[str, str]]] = {}
    declared_fq_type_sources: dict[str, list[tuple[str, str]]] = {}
    for path, source in java_sources.items():
        package_name, type_names, _, _ = source_metadata(source)
        for type_name in type_names:
            declared_type_sources.setdefault(type_name, []).append((path, source))
            if package_name:
                declared_fq_type_sources.setdefault(
                    f"{package_name}.{type_name}", []).append((path, source))

    def typed(type_name: str) -> list[tuple[str, str]]:
        return declared_type_sources.get(type_name, [])

    def fq_typed(fq_type_name: str) -> list[tuple[str, str]]:
        return declared_fq_type_sources.get(fq_type_name, [])

    def required_type(type_name: str) -> tuple[str, str]:
        matches = typed(type_name)
        if len(matches) != 1:
            details.append(f"expected one production type {type_name}, found {len(matches)}")
            return "", ""
        return matches[0]

    operation_request = source_ending(
        sources, "operation/operation/OperationRequest.java", details)
    source_ending(
        sources, "operation/invocation/OperationInvocationPort.java", details)
    invocation_context = source_ending(
        sources, "operation/invocation/OperationInvocationContext.java", details)
    generic_path, generic_service = required_type("CanonicalOperationInvocationService")
    h7_path, h7_service = required_type("TimelineMediaClipOperationService")
    apply_path, apply_service = required_type("OperationPlanApplyService")
    writer_path, timeline_writer = required_type("TimelineRevisionSaveService")
    internal_failure_path, internal_failure_source = required_type("TimelineOperationException")
    workflow_package = source_ending(
        sources, "workflow-module/src/main/java/com/example/platform/workflow/package-info.java", details)

    workflow_sources = {
        path: source for path, source in sources.items()
        if path.startswith("workflow-module/src/main/java/")
    }
    classified_paths: dict[str, str] = {}
    for fq_type_name, role in CLASSIFIED_RUNTIME_FQ_TYPES.items():
        matches = fq_typed(fq_type_name)
        if len(matches) != 1:
            details.append(
                f"runtime role {role} expected one {fq_type_name}, found {len(matches)}")
            continue
        classified_paths[matches[0][0]] = role

    classified_mechanics_paths = {
        matches[0][0]
        for fq_type_name in CLASSIFIED_OPERATION_MECHANICS_FQ_TYPES
        if len(matches := fq_typed(fq_type_name)) == 1
    }
    allowed_reachable_dangerous_paths = (
        set(classified_paths) | classified_mechanics_paths)

    coordination_paths = {path for path in (generic_path, h7_path) if path}
    lower_boundary_paths = {path for path in (apply_path, writer_path) if path}
    _, dependencies, cross_package_references = production_type_graph(
        java_sources, coordination_paths | lower_boundary_paths)
    traversed_paths = reachable_paths(coordination_paths | lower_boundary_paths, dependencies)

    legitimate_direct_dependency_paths: set[str] = set()
    for root_path in coordination_paths | ({apply_path} if apply_path else set()):
        root_source = java_sources.get(root_path, "")
        root_package, _, root_imports, root_words = source_metadata(root_source)
        for dependency_path in dependencies.get(root_path, set()):
            dependency_source = java_sources.get(dependency_path, "")
            dependency_package, dependency_names, _, _ = source_metadata(dependency_source)
            dependency_type = dependency_names[0] if dependency_names else ""
            dependency_fq_type = (
                f"{dependency_package}.{dependency_type}"
                if dependency_package and dependency_type else "")
            imported = any(
                item == dependency_fq_type
                or item.startswith(dependency_fq_type + ".")
                or (item.endswith(".*") and item[:-2] == dependency_package)
                for item in root_imports)
            same_package_reference = (
                dependency_package == root_package and dependency_type in root_words)
            if (dependency_fq_type in KNOWN_RUNTIME_DIRECT_DEPENDENCY_FQ_TYPES
                    and (imported or same_package_reference
                         or dependency_path not in changed_paths)):
                legitimate_direct_dependency_paths.add(dependency_path)

    unclassified_paths: set[str] = set()
    dangerous_runtime_pattern = re.compile(
        r"\b(?:OperationInvocation(?:Port|Context|Result|FailureCode|Exception)|"
        r"CanonicalOperationInvocationService|TimelineMediaClipOperationService|"
        r"TimelineRevisionSaveService|TimelineRevisionRefMutation|"
        r"TimelineRevisionRefHeadUpdateAdapter|HeadUpdatePort|"
        r"OperationRequestResolver|OperationInstance|OperationPlanApplyService|"
        r"OperationPlanner|TextOperationPlanner|OperationPlan|ApplyResult|"
        r"saveRevision(?:ForCommand)?|insertRevision|advanceHead|updateHead|"
        r"moveRef|writeRef|setHead|findLatest|loadLatest|resolveLatest|"
        r"findCurrent|loadCurrent|currentHead|readRef)\b|"
        r"(?:getMessage\s*\(\s*\)|\b(?:message|reason|detail)\b)\s*"
        r"\.\s*(?:contains|startsWith|endsWith|matches|equalsIgnoreCase)\s*\(")

    # A direct new helper is unclassified even before it acquires a forbidden
    # mechanic. This is the fail-closed call-boundary census.
    for root_path in coordination_paths | ({apply_path} if apply_path else set()):
        for dependency_path in dependencies.get(root_path, set()):
            dependency_type = primary_type(java_sources.get(dependency_path, ""))
            if (dependency_type
                    and dependency_path not in legitimate_direct_dependency_paths
                    and dependency_path not in classified_paths
                    and dependency_path not in classified_mechanics_paths):
                unclassified_paths.add(dependency_path)

    for path in traversed_paths:
        source = java_sources.get(path, "")
        if (dangerous_runtime_pattern.search(source)
                and path not in allowed_reachable_dangerous_paths):
            unclassified_paths.add(path)

    invocation_reference = re.compile(
        r"\b(?:OperationInvocation[A-Za-z0-9_$]*|[A-Za-z0-9_$]*OperationInvocation[A-Za-z0-9_$]*|"
        r"CanonicalOperationInvocationService|TimelineMediaClipOperationService)\b")
    h8_runtime_ownership = re.compile(
        r"(?i)(?:\bh8[A-Za-z0-9_$]*\b.{0,80}\b(?:operation|invocation|runtime)\b|"
        r"\b(?:operation|invocation|runtime)[A-Za-z0-9_$]*\b.{0,80}\bh8\b)")
    for path, source in java_sources.items():
        if path in classified_paths:
            continue
        package_match = PACKAGE.search(source)
        invocation_package_owner = bool(
            package_match and package_match.group(1).endswith(".operation.invocation"))
        if (invocation_reference.search(source) or invocation_package_owner
                or h8_runtime_ownership.search(
                    path + "\n" + source)):
            unclassified_paths.add(path)

    # The existing H7 transport projection is classified semantically below;
    # it binds the delegated service only for preview/authorizeAndApply and is
    # not an OperationInvocation route.
    legacy_h7_http_paths = {
        path for path, source in java_sources.items()
        if re.search(r"@(?:[\w.]+\.)?RestController\b", source)
        and "TimelineMediaClipOperationService" in source
        and re.search(r"\.\s*preview\s*\(", source)
        and re.search(r"\.\s*authorizeAndApply\s*\(", source)
        and not re.search(r"\bOperationInvocation(?:Port|Context|Result|Exception)\b", source)
        and not re.search(r"\.\s*invoke\s*\(", source)
    }
    unclassified_paths -= legacy_h7_http_paths

    fully_qualified_escape_edges: set[tuple[str, str]] = set()
    public_workflow_roles = {
        "public-contract", "public-result-authority", "public-failure-authority",
    }
    for source_path, target_paths in cross_package_references.items():
        for target_path in target_paths:
            target_source = java_sources.get(target_path, "")
            target_package, _, _, _ = source_metadata(target_source)
            target_role = classified_paths.get(target_path)
            if source_path in workflow_sources and (
                    (target_package.startswith("com.example.platform.operation")
                     and target_role not in public_workflow_roles)
                    or target_role in {
                        "canonical-timeline-writer", "canonical-head-port",
                        "canonical-head-authority", "canonical-head-adapter",
                        "canonical-adapter", "delegated-coordinator", "lower-apply-boundary",
                    }):
                fully_qualified_escape_edges.add((source_path, target_path))
            if source_path in traversed_paths and target_path in unclassified_paths:
                fully_qualified_escape_edges.add((source_path, target_path))

    for path in sorted(unclassified_paths):
        details.append(f"unclassified H8 invocation runtime source: {path}")

    runtime_extension_sources = {
        path: java_sources[path] for path in unclassified_paths if path in java_sources
    }
    extension_text = "\n".join(runtime_extension_sources.values())
    adapter_and_extensions = generic_service + "\n" + extension_text
    delegated_and_extensions = h7_service + "\n" + extension_text
    invocation_runtime = (
        generic_service + "\n" + h7_service + "\n" + internal_failure_source
        + "\n" + extension_text)
    workflow = "\n".join(workflow_sources.values())
    workflow_imports = [item.strip() for source in workflow_sources.values()
                        for item in IMPORT.findall(source)]

    request_authorities = 0
    request_peers = 0
    for path, source in sources.items():
        if path.endswith("operation/operation/OperationRequest.java"):
            request_authorities += occurrences(
                r"\brecord\s+OperationRequest\b", source)
        elif duplicates_operation_request_semantics(path, source):
            request_peers += 1

    port_authorities = declarations(
        sources, r"\binterface\s+(?:[A-Za-z_$][A-Za-z0-9_$]*)?OperationInvocationPort\b",
        "OperationInvocationPort")
    implementations = declarations(
        sources, r"\b(?:class|record)\s+[A-Za-z_$][A-Za-z0-9_$]*[^\{]*"
                 r"\bimplements\s+[^\{;]*\bOperationInvocationPort\b",
        "OperationInvocationPort")

    workflow_names = [name for source in workflow_sources.values()
                      for name in TYPE_DECLARATION.findall(source)]
    peer_port_names = {
        "CanonicalOperationInvocationPort", "OperationInvocationPort",
        "WorkflowOperationInvocation", "WorkflowOperationNodeExecutor",
    }
    workflow_peer_ports = sum(
        name in peer_port_names
        or bool(re.fullmatch(r"(?i)(?=.*operation)(?=.*invocation).*(port|gateway|authority|executor)", name))
        for name in workflow_names)
    result_names = {
        "CanonicalOperationResult", "CanonicalOperationException", "OperationInvocationResult",
        "OperationInvocationException", "OperationInvocationFailureCode",
    }
    workflow_peer_results = sum(
        name in result_names
        or bool(re.fullmatch(r"(?i)(?=.*operation).*(result|exception|failure|failurecode|error)", name))
        for name in workflow_names)

    workflow_plan_usage = occurrences(r"\bOperationPlan\b", workflow)
    workflow_planner_usage = occurrences(
        r"\b(?:OperationRequestResolver|OperationInstance|OperationPlanner|"
        r"TextOperationPlanner)\b", workflow)
    workflow_writer_usage = occurrences(
        r"\b(?:TimelineRevisionSaveService|TimelineRevisionRefMutation|"
        r"TimelineRevisionRepository|TimelineRevisionPersistencePort|"
        r"TimelineRevisionRefHeadUpdateAdapter|HeadUpdatePort|Timeline[A-Za-z0-9_$]*Writer|"
        r"Timeline[A-Za-z0-9_$]*(?:Repository|Persistence))\b", workflow)
    generic_patch = r"\b(?:TimelinePatchApplicationService|TimelinePatchEngine|TimelinePatch)\b"
    workflow_jooq_usage = occurrences(
        r"\borg\.jooq(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\b|\bDSLContext\b|"
        r"\b(?:Tables|Keys|Indexes|Sequences)\s*\.|"
        r"\bcom\.example\.platform\.[A-Za-z0-9_$.]*jooq[A-Za-z0-9_$.]*\b",
        workflow)
    canonical_writer = (
        r"\b(?:saveRevision|advanceHead|advanceTimelineHead|updateHead|OperationPlanApplyService|"
        r"TimelineRevisionSaveService|TimelineRevisionRefMutation|TimelineRevisionRefHeadUpdateAdapter|"
        r"TimelinePatchApplicationService|TimelinePatchEngine|TimelinePatch)\b")

    unexposed_imports = sum(
        imported.startswith("com.example.platform.operation.")
        and imported != "com.example.platform.operation.invocation.*"
        and imported not in WORKFLOW_ALLOWED_OPERATION_IMPORTS
        for imported in workflow_imports)
    invocation_dependency = occurrences(r'"operation\s*::\s*invocation"', workflow_package)
    broad_dependency = occurrences(r'"operation"', workflow_package)

    broad_exposure = 0
    for path, source in sources.items():
        if path.endswith((
                "operation/operation/package-info.java",
                "operation/plan/package-info.java",
                "operation/invocation/package-info.java")):
            broad_exposure += occurrences(
                r"@(?:org\.springframework\.modulith\.)?NamedInterface\(\s*\"invocation\"\s*\)",
                source)

    contract_sources: list[str] = []
    contract_paths: set[str] = set()
    annotation_graph_errors = 0
    for suffix in CONTRACT_SUFFIXES:
        type_name = Path(suffix).stem
        matches = fq_typed(CONTRACT_FQ_TYPES[type_name])
        if len(matches) != 1:
            details.append(
                f"invocation contract role {type_name} expected one source, found {len(matches)}")
            annotation_graph_errors += 1
            continue
        path, source = matches[0]
        contract_paths.add(path)
        contract_sources.append(source)
        public_names = PUBLIC_TYPE_DECLARATION.findall(source)
        annotated_names = ANNOTATED_PUBLIC_TYPE.findall(source)
        if public_names != annotated_names:
            annotation_graph_errors += max(1, len(set(public_names) ^ set(annotated_names)))
            details.append(f"invocation annotation graph mismatch: {path}")
    contract_text = "\n".join(contract_sources)
    forbidden_signatures = sum(
        occurrences(r"\b" + re.escape(name) + r"\b", contract_text)
        for name in FORBIDDEN_SIGNATURE_TYPES)
    for path, source in sources.items():
        if path in contract_paths:
            continue
        for match in ANNOTATED_PUBLIC_TYPE.finditer(source):
            forbidden_signatures += 1

    request_header = operation_request[:operation_request.find("{")] if "{" in operation_request else operation_request
    context_header_end = invocation_context.find("{")
    context_header = invocation_context[:context_header_end] if context_header_end >= 0 else invocation_context
    authority_headers = request_header + "\n" + context_header
    request_actor = request_controlled_actor_identifiers(request_header)
    request_actor += sum(
        request_controlled_actor_identifiers(source)
        for path, source in java_sources.items()
        if duplicates_operation_request_semantics(path, source))
    request_admin = occurrences(
        r"\b(?:admin|isAdmin|administrator|roles?|permissions?)\b", authority_headers)
    duplicate_tenant = occurrences(r"\b(?:String|TenantId)\s+tenantId\b", authority_headers)

    workflow_provenance = occurrences(
        r"\bOperationInvocationContext\s*\.\s*Provenance\b|"
        r"\bProvenance\s+[A-Za-z_$][A-Za-z0-9_$]*", workflow)
    h8_surface = contract_text + "\n" + generic_service
    provider_fields = occurrences(
        r"(?i)\b[A-Za-z_$][\w.$<>]*[ \t]+provider[A-Za-z0-9_$]*[ \t]*[;,)]",
        h8_surface)
    worker_fields = occurrences(
        r"(?i)\b[A-Za-z_$][\w.$<>]*[ \t]+worker[A-Za-z0-9_$]*[ \t]*[;,)]",
        h8_surface)
    device_fields = occurrences(
        r"(?i)\b[A-Za-z_$][\w.$<>]*[ \t]+device[A-Za-z0-9_$]*[ \t]*[;,)]",
        h8_surface)
    storage_imports = sum(
        imported.startswith(("com.example.platform.storage.", "org.apache.opendal."))
        for imported in IMPORT.findall(generic_service))

    mutable_latest = occurrences(
        r"(?i)\b(?:find|load|get|read|resolve|select)[A-Za-z0-9_$]*"
        r"(?:latest|newest|current|head|recent|last)[A-Za-z0-9_$]*\s*\(|"
        r"\b(?:latestRevision|currentRevision|currentHead|readRef)\s*\(",
        invocation_runtime)
    mutable_latest += occurrences(
        r"(?i)\b(?:max|maximum|highest|greatest)[A-Za-z0-9_$]*"
        r"(?:revision|ref)[A-Za-z0-9_$]*\s*\(|"
        r"\b(?:revision|ref)[A-Za-z0-9_$]*\s*\.\s*"
        r"(?:stream\s*\(\s*\)\s*\.\s*)?max\s*\(|"
        r"\b(?:revision|ref)[A-Za-z0-9_$]*.{0,160}\.\s*desc\s*\(\s*\)"
        r".{0,160}(?:findFirst|limit\s*\(\s*1\s*\)|fetchOne)\s*\(",
        invocation_runtime)
    mutable_latest += occurrences(
        r"(?i)\b(?:revision|ref)[A-Za-z0-9_$]*.{0,160}\.\s*max\s*\(|"
        r"\b(?:Collections|Stream)\s*\.\s*max\s*\([^;)]*"
        r"(?:revision|ref)[A-Za-z0-9_$]*",
        invocation_runtime)
    # Endpoint selection after revision ordering is mutable-head inference even
    # when it is spelled without latest/head vocabulary.
    mutable_latest += occurrences(
        r"(?i)\.\s*sorted\s*\([^;]{0,900}(?:revision|revisionid|revisionnumber|ref)"
        r"[^;]{0,900}\)\s*(?:\.\s*(?:toList|collect)\s*\([^;]*\)\s*)?\.\s*"
        r"(?:(?:findFirst|findLast|first|last)\s*\(\s*\)|get\s*\(\s*0\s*\))",
        invocation_runtime)
    mutable_latest += occurrences(
        r"(?i)\b([A-Za-z_$][A-Za-z0-9_$]*)\s*\.\s*sort\s*\("
        r"[^;]{0,700}(?:revision|revisionid|revisionnumber|ref)[^;]*;"
        r".{0,500}\b\1\s*\.\s*(?:get\s*\(\s*0\s*\)|first\s*\(\s*\)|"
        r"last\s*\(\s*\)|getFirst\s*\(\s*\)|getLast\s*\(\s*\))",
        invocation_runtime)
    mutable_latest += occurrences(
        r"(?i)\b(?:Comparator(?:\s*<[^;=]+>)?|var)\s+"
        r"([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
        r"[^;]{0,700}(?:revision|revisionid|revisionnumber|ref)[^;]{0,700}"
        r"(?:reversed|reverseOrder|desc)\s*\([^;]*;.{0,500}"
        r"\b([A-Za-z_$][A-Za-z0-9_$]*)\s*\.\s*sort\s*\(\s*\1\s*\)\s*;"
        r".{0,500}\b\2\s*\.\s*(?:get\s*\(\s*0\s*\)|getFirst\s*\(\s*\))",
        invocation_runtime)
    mutable_latest += occurrences(
        r"(?i)\.\s*(?:max|min)\s*\([^;]{0,700}"
        r"(?:revision|revisionid|revisionnumber|ref)[^;]{0,700}\)",
        invocation_runtime)
    reflection = occurrences(
        r"\b(?:Class\s*\.\s*forName|java\.lang\.reflect|MethodHandles|"
        r"getDeclaredMethod|getMethod\s*\()", invocation_runtime)
    generic_planner = occurrences(
        r"\b(?:OperationPlanner|OperationRequestResolver|OperationRegistry|"
        r"OperationDefinitions|TextOperationPlanner)\b", adapter_and_extensions)

    generic_invoke = method_body(generic_service, "invoke")
    exact_definition_id_check = re.compile(
        r"!\s*OperationDefinition\s*\.\s*V1\s*\.\s*ADD_MEDIA_CLIP\s*\.\s*"
        r"definitionId\s*\(\s*\)\s*\.\s*equals\s*\(\s*request\s*\.\s*"
        r"definitionId\s*\(\s*\)\s*\)")
    exact_definition_version_check = re.compile(
        r"!\s*OperationDefinition\s*\.\s*V1\s*\.\s*ADD_MEDIA_CLIP\s*\.\s*"
        r"version\s*\(\s*\)\s*\.\s*equals\s*\(\s*request\s*\.\s*"
        r"version\s*\(\s*\)\s*\)")
    exact_definition_checks = (
        len(exact_definition_id_check.findall(generic_invoke)) == 1
        and len(exact_definition_version_check.findall(generic_invoke)) == 1)
    definition_dispatch_surface = generic_invoke + "\n" + extension_text
    definition_remainder = exact_definition_id_check.sub("", definition_dispatch_surface)
    definition_remainder = exact_definition_version_check.sub("", definition_remainder)
    alternate_definition_reference = bool(re.search(
        r"\bOperationDefinition(?:s)?\b|\b[A-Za-z_$][A-Za-z0-9_$]*Definition\s*\.\s*"
        r"(?:ALL|VALUES|entries|registry)\b",
        definition_remainder))
    executable_identities = set(re.findall(
        r"OperationDefinition\s*\.\s*V1\s*\.\s*([A-Z][A-Z0-9_]*)\s*"
        r"\.\s*definitionId\s*\(\s*\)", generic_invoke))
    executable_versions = set(re.findall(
        r"OperationDefinition\s*\.\s*V1\s*\.\s*([A-Z][A-Z0-9_]*)\s*"
        r"\.\s*version\s*\(\s*\)", generic_invoke))
    executable_definitions = executable_identities & executable_versions
    unbounded_dispatch = occurrences(
        r"(?i)\b(?:operation|definition|capability)[A-Za-z0-9_$]*"
        r"(?:registry|list|all|stream|finder|resolver|handlers?|dispatch|capabilities)\b|"
        r"\b(?:registry|list|all|stream|find|resolve|select|lookup|capability)"
        r"[A-Za-z0-9_$]*(?:operation|definition|handler|executor)[A-Za-z0-9_$]*\b|"
        r"\b(?:registeredDefinitions?|definitionHandlers?|dispatchTable|capabilities)\b|"
        r"\.\s*(?:stream|findFirst|findAny|resolve|select|lookup)\s*\(|"
        r"\b(?:switch)\s*\([^)]*request\s*\.\s*(?:definitionId|version)\s*\(|"
        r"\.\s*(?:get|compute|computeIfAbsent|computeIfPresent|lookup)\s*\(\s*"
        r"request\s*\.\s*(?:definitionId|version)\s*\(\s*\)",
        definition_dispatch_surface)
    unbounded_dispatch += occurrences(
        r"\b(?:Map(?:\s*<[^;=]+>)?|var)\s+"
        r"([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:Map\s*\.\s*of|new\s+"
        r"[A-Za-z_$][A-Za-z0-9_$.]*Map)\b[^;]*;.{0,700}"
        r"\b\1\s*\.\s*(?:get|compute|computeIfAbsent|computeIfPresent)\s*\(",
        definition_dispatch_surface)
    if (reflection or generic_planner or unbounded_dispatch
            or alternate_definition_reference or not exact_definition_checks):
        executable_definition_count = 0
    elif executable_definitions == set(FROZEN_EXECUTABLE_DEFINITIONS):
        # OperationDefinition.V1.ADD_MEDIA_CLIP is the frozen
        # timeline.media-clip.add@1.0 definition. Both identity and version
        # accessors must participate in the bounded dispatch check above.
        executable_definition_count = 1
    elif set(FROZEN_EXECUTABLE_DEFINITIONS).issubset(executable_definitions):
        executable_definition_count = len(executable_definitions)
    else:
        executable_definition_count = 0

    result_definition_count = declarations(
        java_sources,
        r"\b(?:class|interface|record|enum)\s+OperationInvocationResult\b",
        "OperationInvocationResult")
    duplicate_result_authorities = max(0, result_definition_count - 1)

    duplicate_failure_authorities = 0
    runtime_authority_sources = {
        path: source for path, source in java_sources.items()
        if path in contract_paths
        or path in {generic_path, h7_path, internal_failure_path}
        or path in runtime_extension_sources
    }
    for path, source in runtime_authority_sources.items():
        package_name, owner_names, _, _ = source_metadata(source)
        owner = owner_names[0] if owner_names else ""
        owner_fq = f"{package_name}.{owner}" if package_name and owner else ""
        allowed_results = INTERNAL_RESULT_PROJECTIONS.get(owner_fq, set())
        allowed_failures = INTERNAL_FAILURE_PROJECTIONS.get(owner_fq, set())
        for name in TYPE_DECLARATION.findall(source):
            if re.search(r"(?:Result|Outcome)$", name):
                if name != "OperationInvocationResult" and name not in allowed_results:
                    duplicate_result_authorities += 1
            if re.search(r"(?:Exception|Failure|FailureCode|Error|Fault|Code)$", name):
                if name not in {
                        "OperationInvocationFailureCode", "OperationInvocationException"
                } and name not in allowed_failures:
                    duplicate_failure_authorities += 1

    alternate_result_authorities: set[str] = set()
    alternate_failure_authorities: set[str] = set()
    invocation_authority_context_paths = set(workflow_sources) | traversed_paths
    for path in invocation_authority_context_paths:
        source = java_sources.get(path, "")
        results, failures = invocation_semantic_authorities(
            path, source,
            workflow_owned=path in workflow_sources,
            called_runtime_helper=path in traversed_paths)
        alternate_result_authorities.update(results)
        alternate_failure_authorities.update(failures)
    alternate_result_authority_escapes = len(alternate_result_authorities)
    alternate_failure_authority_escapes = len(alternate_failure_authorities)

    for canonical_name in (
            "OperationInvocationFailureCode", "OperationInvocationException"):
        duplicate_failure_authorities += max(
            0, len(typed(canonical_name)) - 1)

    raw_string_failure_inference = raw_failure_inference_occurrences(invocation_runtime)

    base_hash_fallback = occurrences(
        r"baseContentHash\s*(?:\(\s*\))?\s*"
        r"(?:==\s*null|\.\s*isBlank\s*\(\s*\))"
        r".{0,240}\b(?:findLatest|loadLatest|resolveLatest|findCurrent|loadCurrent|"
        r"currentHead|latestRevision|readRef)\s*\(",
        invocation_runtime)
    missing_exact_base_hash_fail_closed = (
        int(not exact_base_hash_fail_closed(operation_request))
        + base_hash_fallback)

    public_route_sources = {
        path: source for path, source in java_sources.items()
        if ("/web/" in f"/{path}"
             or any(name.endswith("Controller") for name in TYPE_DECLARATION.findall(source))
             or re.search(
                 r"@(?:[\w.]+\.)?(?:RestController|Controller|RequestMapping|GetMapping|"
                 r"PostMapping|PutMapping|PatchMapping|DeleteMapping)\b", source))
    }
    contract_or_canonical_http = sum(
        1 for source in public_route_sources.values()
        if re.search(
            r"\b(?:OperationInvocationPort|OperationInvocationContext|"
            r"OperationInvocationResult|OperationInvocationException|"
            r"CanonicalOperationInvocationService)\b", source))
    timeline_http_paths = {
        path for path, source in public_route_sources.items()
        if re.search(r"\bTimelineMediaClipOperationService\b", source)
    }
    unauthorized_timeline_http = len(timeline_http_paths - legacy_h7_http_paths)
    legacy_h7_http_census_drift = abs(len(legacy_h7_http_paths) - 1)
    public_http_route_authorities = (
        contract_or_canonical_http
        + unauthorized_timeline_http
        + legacy_h7_http_census_drift)

    normalized_schema_markers = (
        "operationinvocation", "operationrequest", "basecontenthash", "h8runtimejob",
    )

    def has_normalized_schema_marker(path: str, source: str) -> bool:
        normalized = re.sub(r"[^A-Za-z0-9]", "", path + "\n" + source).lower()
        return any(marker in normalized for marker in normalized_schema_markers)

    central_flyway_changes = {
        path for path in changed_paths if path.startswith(CENTRAL_FLYWAY_ROOT)
    }
    central_generated_jooq_changes = {
        path for path in changed_paths if path.startswith(CENTRAL_GENERATED_JOOQ_ROOT)
    }
    central_schema_generation_changes = {
        path for path in changed_paths
        if path.startswith(CENTRAL_SCHEMA_GENERATION_ROOT)
        and not path.startswith(CENTRAL_GENERATED_JOOQ_ROOT)
    }
    schema_mutation_authorities = sum(
        1 for path, source in sources.items()
        if path.endswith(".sql")
        and (has_normalized_schema_marker(path, source)
             or "timeline.media-clip.add" in (path + "\n" + source).lower()))
    schema_mutation_authorities += (
        len(central_flyway_changes) + len(central_schema_generation_changes))
    jooq_generated_mutation_authorities = sum(
        1 for path, source in java_sources.items()
        if "jooq" in path.lower() and "generated" in path.lower()
        and (has_normalized_schema_marker(path, source)
             or re.search(
                 r"(?i)(?:timeline\.media-clip\.add|CanonicalOperationInvocationService|"
                 r"TimelineMediaClipOperationService)", path + "\n" + source)))
    jooq_generated_mutation_authorities += len(central_generated_jooq_changes)
    camelcase_schema_marker_escapes = sum(
        1 for path, source in sources.items()
        if (path.endswith(".sql") or ("jooq" in path.lower() and "generated" in path.lower()))
        and has_normalized_schema_marker(path, source))
    delegate = "mediaClipService.invoke(request, context)"
    unsupported_missing = int(not exact_unsupported_operation_guard(
        generic_invoke, delegate))

    pipeline_violations = 0
    generic_sequence = (
        "OperationInvocationFailureCode.INVALID_REQUEST",
        "OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId()",
        "OperationTargetRequest.TimelineTargetRequest",
        "OperationParameters.AddMediaClipParameters",
        "context.actor()",
        delegate,
    )
    pipeline_violations += int(not ordered(generic_invoke, generic_sequence))
    pipeline_violations += int(generic_invoke.count(delegate) != 1)
    public_apply_body = method_body(h7_service, "authorizeAndApply")
    public_preview_body = method_body(h7_service, "preview")
    internal_invoke_body = method_body(h7_service, "invoke")
    internal_prepare_body = method_body(h7_service, "prepareInternal")
    execute_body = method_body(h7_service, "executePrepared")
    prepare_body = method_body(h7_service, "prepare")
    preparation_authorization_body = method_body(
        h7_service, "requirePreparationAuthorization")
    pipeline_violations += int(not ordered(execute_body, (
        "prepared.plan().planDigest().equals(expectedPlanDigest)",
        "authorizationPort.decide", "operationPlanDigest", "AuthorizationDecision",
        "ApplyContext", "applyService.apply")))
    pipeline_violations += int(not exact_authorization_decision_binding(execute_body))
    pipeline_violations += int(
        not has_required_preparation_authorization_first(public_apply_body)
        or not ordered(public_apply_body, (
            "requirePreparationAuthorization", "PreparedOperation prepared = prepare",
            "executePrepared")))
    pipeline_violations += int(
        not has_required_preparation_authorization_first(public_preview_body)
        or not ordered(public_preview_body, (
            "requirePreparationAuthorization",
            "prepare(tenantId, projectId, toOperationRequest")))
    pipeline_violations += int(
        not has_required_preparation_authorization_first(internal_prepare_body)
        or not ordered(internal_prepare_body, (
            "requirePreparationAuthorization", "prepare(tenantId, projectId, request)")))
    pipeline_violations += int(
        not authorization_helper_fails_closed(preparation_authorization_body))
    pipeline_violations += int(not ordered(internal_invoke_body, (
        "prepareInternal", "executePrepared", "prepared.plan().planDigest()")))
    pipeline_violations += int(not ordered(prepare_body, (
        "findById", "timelineContentDigest", "findPayloadDocument",
        "OperationRequestResolver.resolve", "sourceValidator.validate", "planner.plan",
        "timelineValidator.validateDocument", "OperationPlanPreview.of")))

    exact_digest_mismatch_branch = re.compile(
        r"if\s*\(\s*!\s*prepared\s*\.\s*plan\s*\(\s*\)\s*\.\s*planDigest\s*"
        r"\(\s*\)\s*\.\s*equals\s*\(\s*expectedPlanDigest\s*\)\s*\)\s*"
        r"\{(?P<body>[^{}]*)\}", flags=re.DOTALL)
    digest_mismatch_branches = list(exact_digest_mismatch_branch.finditer(execute_body))
    exact_fail_closed_digest_branch = (
        len(digest_mismatch_branches) == 1
        and is_exact_plan_changed_throw(digest_mismatch_branches[0].group("body"))
        and has_exact_leading_digest_guard(execute_body))
    nullable_digest_gate = re.compile(
        r"(?:expectedPlanDigest\s*!=\s*null|Objects\s*\.\s*nonNull\s*"
        r"\(\s*expectedPlanDigest\s*\))\s*(?:&&|\?).{0,240}"
        r"prepared\s*\.\s*plan\s*\(\s*\)\s*\.\s*planDigest",
        flags=re.DOTALL)
    internal_digest_argument = re.compile(
        r"executePrepared\s*\(\s*tenantId\s*,\s*target\s*\.\s*timelineId\s*\(\s*\)\s*,\s*"
        r"prepared\s*,\s*prepared\s*\.\s*plan\s*\(\s*\)\s*\.\s*planDigest\s*\(\s*\)\s*,\s*"
        r"context\s*\.\s*invocationId\s*\(\s*\)",
        flags=re.DOTALL)
    null_expected_plan_digest_bypass = (
        int(not exact_fail_closed_digest_branch)
        + int(occurrences(r"\bexpectedPlanDigest\b", execute_body) != 1)
        + len(nullable_digest_gate.findall(execute_body))
        + int(not internal_digest_argument.search(internal_invoke_body))
        + occurrences(r"executePrepared\s*\([^;]{0,500},\s*null\s*,", internal_invoke_body)
    )

    writer_definitions = declarations(
        sources, r"\bclass\s+TimelineRevisionSaveService\b", "TimelineRevisionSaveService")
    head_definitions = declarations(
        sources, r"\bclass\s+TimelineRevisionRefMutation\b", "TimelineRevisionRefMutation")
    h8_writer_authority = occurrences(
        r"\b(?:TimelineRevisionSaveService|TimelineRevisionRepository|"
        r"TimelineRevisionPersistence|TimelineRevisionRefMutation|HeadUpdatePort|"
        r"saveRevision|insertRevision|advanceHead|updateHead)\b", adapter_and_extensions)
    h8_writer_authority += occurrences(
        r"\.\s*(?:saveRevision|saveRevisionForCommand|insertRevision|"
        r"advanceHead|updateHead)\s*\(", h7_service)
    h8_head_authority = occurrences(
        r"\b(?:TimelineRevisionRefMutation|TimelineRevisionRefHeadUpdateAdapter|HeadUpdatePort|"
        r"advanceHead|updateHead|moveRef|writeRef|setHead)\b",
        delegated_and_extensions + "\n" + generic_service)
    h8_plan_authority = occurrences(
        r"\b(?:OperationPlan|OperationPlanner|OperationPlanApplyService|PlannedChange)\b",
        adapter_and_extensions)

    counts = {
        "OPERATION_REQUEST_AUTHORITY_COUNT": request_authorities,
        "NEW_OPERATION_REQUEST_PEER_TYPE_COUNT": request_peers,
        "OPERATION_INVOCATION_PORT_AUTHORITY_COUNT": port_authorities,
        "OPERATION_INVOCATION_PORT_IMPLEMENTATION_COUNT": implementations,
        "WORKFLOW_PEER_INVOCATION_PORT_COUNT": workflow_peer_ports,
        "WORKFLOW_PEER_OPERATION_RESULT_AUTHORITY_COUNT": workflow_peer_results,
        "LEGACY_WORKFLOW_INVOCATION_PORT_DEFINITION_COUNT": sum(
            name in {"CanonicalOperationInvocationPort", "OperationInvocationPort"}
            for name in workflow_names),
        "LEGACY_WORKFLOW_OPERATION_RESULT_DEFINITION_COUNT": sum(
            name in {"CanonicalOperationResult", "CanonicalOperationException"}
            for name in workflow_names),
        "WORKFLOW_OPERATION_PLAN_IMPORT_COUNT": workflow_plan_usage,
        "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT": workflow_planner_usage,
        "WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT": workflow_writer_usage,
        "WORKFLOW_GENERIC_TIMELINE_PATCH_USAGE_COUNT": occurrences(generic_patch, workflow),
        "WORKFLOW_JOOQ_IMPORT_COUNT": workflow_jooq_usage,
        "WORKFLOW_CANONICAL_MEDIA_WRITER_COUNT": occurrences(canonical_writer, workflow),
        "WORKFLOW_OPERATION_RESULT_REAUTHORING_COUNT": workflow_peer_results,
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT": len(fully_qualified_escape_edges),
        "WORKFLOW_TO_OPERATION_UNEXPOSED_DEPENDENCY_COUNT": unexposed_imports,
        "WORKFLOW_OPERATION_INVOCATION_ALLOWED_DEPENDENCY_COUNT": invocation_dependency,
        "WORKFLOW_BROAD_OPERATION_ALLOWED_DEPENDENCY_COUNT": broad_dependency,
        "BROAD_OPERATION_PACKAGE_EXPOSURE_COUNT": broad_exposure,
        "FORBIDDEN_INVOCATION_PUBLIC_SIGNATURE_TYPE_COUNT": forbidden_signatures,
        "REQUEST_CONTROLLED_ACTOR_AUTHORITY_COUNT": request_actor,
        "REQUEST_CONTROLLED_ADMIN_AUTHORITY_COUNT": request_admin,
        "DUPLICATE_TENANT_AUTHORITY_COUNT": duplicate_tenant,
        "WORKFLOW_PROVENANCE_AS_CANONICAL_AUTHORITY_COUNT": workflow_provenance,
        "H8_PROVIDER_BINDING_FIELD_COUNT": provider_fields,
        "H8_WORKER_BINDING_FIELD_COUNT": worker_fields,
        "H8_DEVICE_BINDING_FIELD_COUNT": device_fields,
        "H8_STORAGE_IMPLEMENTATION_IMPORT_COUNT": storage_imports,
        "MUTABLE_LATEST_FALLBACK_COUNT": mutable_latest,
        "REFLECTION_OPERATION_EXECUTION_FALLBACK_COUNT": reflection,
        "GENERIC_OPERATION_PLANNER_FALLBACK_COUNT": generic_planner,
        "EXECUTABLE_OPERATION_DEFINITION_COUNT": executable_definition_count,
        "DUPLICATE_OPERATION_INVOCATION_RESULT_AUTHORITY_COUNT": duplicate_result_authorities,
        "DUPLICATE_OPERATION_INVOCATION_FAILURE_AUTHORITY_COUNT": duplicate_failure_authorities,
        "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT": (
            alternate_result_authority_escapes),
        "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT": (
            alternate_failure_authority_escapes),
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT": raw_string_failure_inference,
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT": (
            missing_exact_base_hash_fail_closed),
        "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT": public_http_route_authorities,
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT": schema_mutation_authorities,
        "H8_JOOQ_GENERATED_MUTATION_AUTHORITY_COUNT": jooq_generated_mutation_authorities,
        "CAMELCASE_SCHEMA_MARKER_ESCAPE_COUNT": camelcase_schema_marker_escapes,
        "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT": unsupported_missing,
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT": pipeline_violations,
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT": null_expected_plan_digest_bypass,
        "H8_NEW_TIMELINE_WRITER_COUNT": abs(writer_definitions - 1) + h8_writer_authority,
        "H8_NEW_HEAD_AUTHORITY_COUNT": h8_head_authority,
        "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT": h8_plan_authority,
        "CANONICAL_TIMELINE_HEAD_AUTHORITY_COUNT": head_definitions,
        "CANONICAL_TIMELINE_MUTATION_WRITER_AUTHORITY_COUNT": writer_definitions,
        "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT": changed_path_scope_violations,
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT": (
            governed_hash_mismatches),
        "UNCLASSIFIED": len(details) + annotation_graph_errors,
    }
    return Evaluation(counts, tuple(details))


def replace_once(sources: dict[str, str], suffix: str, old: str, new: str) -> dict[str, str]:
    changed = dict(sources)
    matches = [path for path in changed if path.endswith(suffix)]
    if len(matches) != 1 or old not in changed[matches[0]]:
        raise RuntimeError(f"self-test mutation anchor missing: {suffix}: {old}")
    changed[matches[0]] = changed[matches[0]].replace(old, new, 1)
    return changed


def with_source(sources: dict[str, str], path: str, source: str) -> dict[str, str]:
    changed = dict(sources)
    changed[path] = source
    return changed


def replace_source(sources: dict[str, str], suffix: str, source: str) -> dict[str, str]:
    changed = dict(sources)
    matches = [path for path in changed if path.endswith(suffix)]
    if len(matches) != 1:
        raise RuntimeError(f"self-test source anchor missing: {suffix}")
    changed[matches[0]] = source
    return changed


def with_called_helper(
        sources: dict[str, str], helper_name: str, helper_source: str,
        call: str = "run(request)") -> dict[str, str]:
    changed = with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/app/operation/"
        f"{helper_name}.java",
        "package com.example.platform.render.app.operation; " + helper_source)
    return replace_once(
        changed, "CanonicalOperationInvocationService.java",
        "var outcome = mediaClipService.invoke(request, context);",
        f"new {helper_name}().{call};\n"
        "            var outcome = mediaClipService.invoke(request, context);")


def with_fully_qualified_called_helper(
        sources: dict[str, str], helper_name: str, helper_source: str) -> dict[str, str]:
    package_name = "com.example.platform.render.app.operation.escape"
    changed = with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/app/operation/escape/"
        f"{helper_name}.java",
        f"package {package_name}; " + helper_source)
    return replace_once(
        changed, "CanonicalOperationInvocationService.java",
        "var outcome = mediaClipService.invoke(request, context);",
        f"{package_name}.{helper_name}.run(request);\n"
        "            var outcome = mediaClipService.invoke(request, context);")


def with_imported_called_helper(
        sources: dict[str, str], helper_name: str, helper_source: str) -> dict[str, str]:
    package_name = "com.example.platform.render.app.operation.escape"
    changed = with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/app/operation/escape/"
        f"{helper_name}.java",
        f"package {package_name}; " + helper_source)
    changed = replace_once(
        changed, "CanonicalOperationInvocationService.java",
        "package com.example.platform.render.app.operation;\n",
        "package com.example.platform.render.app.operation;\n\n"
        f"import {package_name}.{helper_name};\n")
    return replace_once(
        changed, "CanonicalOperationInvocationService.java",
        "var outcome = mediaClipService.invoke(request, context);",
        f"{helper_name}.run(request);\n"
        "            var outcome = mediaClipService.invoke(request, context);")


def mutated_source_paths(
        original: dict[str, str], mutated: dict[str, str]) -> set[str]:
    return {
        path for path in set(original) | set(mutated)
        if original.get(path) != mutated.get(path)
    }


def append_to_existing_source(
        sources: dict[str, str], path: str, addition: str) -> dict[str, str]:
    if path not in sources:
        raise RuntimeError(f"self-test source anchor missing: {path}")
    return with_source(sources, path, sources[path] + addition)


def run_scope_lifecycle_controls(
        root: Path, sources: dict[str, str],
        changed_paths: set[str]) -> tuple[int, int]:
    """Exercise immutable checkpoint and descendant lifecycle invariants."""
    controls: list[tuple[str, bool, str]] = []

    current_paths, current_errors, current_descendant = (
        historical_h8_changed_paths(root))
    current_evaluation = evaluate(
        sources, current_errors, changed_paths=current_paths)
    controls.append((
        "current_frontend_descendant_head",
        current_descendant and not current_errors and current_evaluation.passed,
        "H8_ACCEPTED_CANONICAL_SHA_ANCESTOR_OF_HEAD"))
    controls.append((
        "historical_h8_delta_exact_authorized_22_paths",
        current_paths == H8_AUTHORIZED_CHANGED_PATHS
        and len(current_paths) == 22,
        "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT"))

    invalid_checkpoint_detected = True
    for invalid_checkpoint in ("0" * 40, "not-a-commit"):
        invalid_paths, invalid_errors, invalid_descendant = (
            historical_h8_changed_paths(
                root, accepted_canonical_sha=invalid_checkpoint))
        invalid_evaluation = evaluate(
            sources, invalid_errors, changed_paths=invalid_paths)
        invalid_checkpoint_detected = invalid_checkpoint_detected and (
            bool(invalid_errors)
            and not invalid_descendant
            and invalid_evaluation.counts["UNCLASSIFIED"] > 0
            and not invalid_evaluation.passed)
    controls.append((
        "missing_and_invalid_accepted_checkpoint_fail_closed",
        invalid_checkpoint_detected,
        "UNCLASSIFIED"))

    non_descendant_paths, non_descendant_errors, non_descendant = (
        historical_h8_changed_paths(
            root, current_head=H8_PRE_CANONICAL_BASE_SHA))
    non_descendant_evaluation = evaluate(
        sources, non_descendant_errors, changed_paths=non_descendant_paths)
    controls.append((
        "checkout_not_descended_from_accepted_fails_closed",
        not non_descendant
        and bool(non_descendant_errors)
        and non_descendant_evaluation.counts["UNCLASSIFIED"] > 0
        and not non_descendant_evaluation.passed,
        "UNCLASSIFIED"))

    checkpoint_paths, checkpoint_errors, checkpoint_descendant = (
        historical_h8_changed_paths(
            root, current_head=H8_ACCEPTED_CANONICAL_SHA))
    descendant_delta_present = False
    command = (
        "git", "diff", "--quiet", H8_ACCEPTED_CANONICAL_SHA, "HEAD", "--")
    try:
        completed = subprocess.run(
            command, cwd=root, check=False, capture_output=True, timeout=30)
        descendant_delta_present = completed.returncode == 1
    except (OSError, subprocess.SubprocessError):
        descendant_delta_present = False
    controls.append((
        "legitimate_descendant_paths_excluded_from_historical_scope",
        descendant_delta_present
        and checkpoint_descendant
        and not checkpoint_errors
        and checkpoint_paths == current_paths == changed_paths
        and evaluate(sources, changed_paths=current_paths).passed,
        "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT"))

    unauthorized_scope = set(changed_paths)
    unauthorized_scope.add(
        "frontend/src/main/typescript/h8-unauthorized-candidate-scope.ts")
    unauthorized_evaluation = evaluate(
        sources, changed_paths=unauthorized_scope)
    controls.append((
        "unauthorized_h8_candidate_scope_mutation",
        unauthorized_evaluation.counts[
            "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT"] > 0
        and not unauthorized_evaluation.passed,
        "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT"))

    failures = 0
    for name, detected, law in controls:
        print(f"H8_MUTATION {name}={'PASS' if detected else 'FAIL'} {law}")
        failures += int(not detected)
    return len(controls), failures


def run_self_test(
        root: Path, sources: dict[str, str], changed_paths: set[str]) -> bool:
    baseline = evaluate(sources, changed_paths=changed_paths)
    if not baseline.passed:
        print("H8_MUTATION baseline=FAIL")
        print("H8_MUTATION_MATRIX_TOTAL=0")
        print("H8_MUTATION_MATRIX_FAILURES=1")
        return False

    cases: list[tuple[str, tuple[str, ...], dict[str, str]]] = []
    cases.append(("duplicate_request_type", ("NEW_OPERATION_REQUEST_PEER_TYPE_COUNT",), with_source(
        sources,
        "operation-module/src/main/java/com/example/platform/operation/operation/peer/OperationRequest.java",
        "package com.example.platform.operation.operation.peer; "
        "public record OperationRequest(String baseRevisionId) {}")))
    cases.append(("second_invocation_port", ("OPERATION_INVOCATION_PORT_AUTHORITY_COUNT",), with_source(
        sources,
        "operation-module/src/main/java/com/example/platform/operation/invocation/PeerOperationInvocationPort.java",
        "package com.example.platform.operation.invocation; interface PeerOperationInvocationPort {}")))
    cases.append(("second_port_implementation", ("OPERATION_INVOCATION_PORT_IMPLEMENTATION_COUNT",), with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/app/operation/PeerInvocationService.java",
        "package com.example.platform.render.app.operation; class PeerInvocationService implements OperationInvocationPort {}")))
    cases.append(("workflow_peer_shadow", ("WORKFLOW_PEER_INVOCATION_PORT_COUNT",), with_source(
        sources,
        "workflow-module/src/main/java/com/example/platform/workflow/Peer.java",
        "package com.example.platform.workflow; interface WorkflowOperationNodeExecutor {}")))
    cases.append(("workflow_operation_plan_import", ("WORKFLOW_OPERATION_PLAN_IMPORT_COUNT",), with_source(
        sources,
        "workflow-module/src/main/java/com/example/platform/workflow/PlanLeak.java",
        "package com.example.platform.workflow;\n"
        "import com.example.platform.operation.plan.OperationPlan;\nclass PlanLeak {}")))
    cases.append(("workflow_timeline_writer_patch", (
        "WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT", "WORKFLOW_GENERIC_TIMELINE_PATCH_USAGE_COUNT"), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/WriterLeak.java",
            "package com.example.platform.workflow;\n"
            "import com.example.platform.timeline.app.TimelineRevisionSaveService;\n"
            "import com.example.platform.timeline.patch.TimelinePatch;\nclass WriterLeak {}")))
    cases.append(("broad_package_exposure", ("BROAD_OPERATION_PACKAGE_EXPOSURE_COUNT",), with_source(
        sources,
        "operation-module/src/main/java/com/example/platform/operation/plan/package-info.java",
        "@org.springframework.modulith.NamedInterface(\"invocation\") package com.example.platform.operation.plan;")))
    cases.append(("context_tenant_admin_authority", (
        "REQUEST_CONTROLLED_ADMIN_AUTHORITY_COUNT", "DUPLICATE_TENANT_AUTHORITY_COUNT"), replace_once(
            sources, "OperationInvocationContext.java", "String invocationId,\n        Provenance provenance",
            "String invocationId,\n        String tenantId,\n        boolean admin,\n        Provenance provenance")))
    cases.append(("provider_runtime_field", ("H8_PROVIDER_BINDING_FIELD_COUNT",), replace_once(
        sources, "OperationInvocationContext.java", "String invocationId,\n        Provenance provenance",
        "String invocationId,\n        String providerRuntimeBinding,\n        Provenance provenance")))
    cases.append(("reflection_fallback", ("REFLECTION_OPERATION_EXECUTION_FALLBACK_COUNT",), replace_once(
        sources, "CanonicalOperationInvocationService.java", "var outcome = mediaClipService.invoke(request, context);",
        "Class.forName(request.definitionId().value());\n            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("generic_planner_fallback", ("GENERIC_OPERATION_PLANNER_FALLBACK_COUNT",), replace_once(
        sources, "CanonicalOperationInvocationService.java", "var outcome = mediaClipService.invoke(request, context);",
        "new OperationPlanner().plan(null, null, null);\n            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("unsupported_fail_closed_removed", (
        "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "OperationInvocationFailureCode.UNSUPPORTED_OPERATION", "OperationInvocationFailureCode.INVALID_REQUEST")))
    cases.append(("delegate_removed", ("OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
        sources, "CanonicalOperationInvocationService.java", "mediaClipService.invoke(request, context)",
        "mediaClipService.toString()")))
    order_break = replace_once(
        sources, "TimelineMediaClipOperationService.java", "sourceValidator.validate(",
        "sourceValidator.check(")
    order_break = replace_once(
        order_break, "TimelineMediaClipOperationService.java",
        "OperationPlanPreview genericPreview = OperationPlanPreview.of(plan);",
        "sourceValidator.validate(null, tenantId, projectId, null);\n"
        "        OperationPlanPreview genericPreview = OperationPlanPreview.of(plan);")
    cases.append(("delegated_pipeline_order_break", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), order_break))
    cases.append(("second_invocation_result_authority", (
        "DUPLICATE_OPERATION_INVOCATION_RESULT_AUTHORITY_COUNT",), with_source(
            sources,
            "operation-module/src/main/java/com/example/platform/operation/invocation/"
            "PeerOperationInvocationResult.java",
            "package com.example.platform.operation.invocation; "
            "record OperationInvocationResult(String value) {}")))
    cases.append(("second_invocation_failure_authority", (
        "DUPLICATE_OPERATION_INVOCATION_FAILURE_AUTHORITY_COUNT",), with_source(
            sources,
            "operation-module/src/main/java/com/example/platform/operation/invocation/"
            "PeerOperationInvocationFailure.java",
            "package com.example.platform.operation.invocation; "
            "final class PeerOperationInvocationFailure extends RuntimeException {}")))
    cases.append(("h8_direct_timeline_writer_access", (
        "H8_NEW_TIMELINE_WRITER_COUNT",), with_source(
            sources,
            "render-module/src/main/java/com/example/platform/render/app/operation/"
            "DirectWriterOperationInvocationService.java",
            "package com.example.platform.render.app.operation; "
            "final class DirectWriterOperationInvocationService implements OperationInvocationPort { "
            "TimelineRevisionSaveService writer; }")))
    cases.append(("h8_timeline_head_mutation_authority", (
        "H8_NEW_HEAD_AUTHORITY_COUNT",), with_source(
            sources,
            "render-module/src/main/java/com/example/platform/render/app/operation/"
            "HeadMutatingOperationInvocationService.java",
            "package com.example.platform.render.app.operation; "
            "final class HeadMutatingOperationInvocationService implements OperationInvocationPort { "
            "HeadUpdatePort headUpdatePort; }")))
    cases.append(("h8_operation_plan_shadow", (
        "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT",), with_source(
            sources,
            "operation-module/src/main/java/com/example/platform/operation/invocation/"
            "InvocationOperationPlanShadow.java",
            "package com.example.platform.operation.invocation; "
            "final class InvocationOperationPlanShadow { OperationPlan plan; }")))
    cases.append(("h8_public_http_route", (
        "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT",), with_source(
            sources,
            "platform-app/src/main/java/com/example/platform/web/H8OperationInvocationController.java",
            "package com.example.platform.web; @RestController "
            "final class H8OperationInvocationController { OperationInvocationPort port; }")))
    cases.append(("h8_schema_mutation_authority", (
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT",), with_source(
            sources,
            "platform-app/src/main/resources/db/migration/V999__h8_invocation.sql",
            "create table operation_invocation (base_content_hash varchar(64) not null);")))
    cases.append(("h8_generated_jooq_mutation_authority", (
        "H8_JOOQ_GENERATED_MUTATION_AUTHORITY_COUNT",), with_source(
            sources,
            "platform-app/build/generated-src/jooq/main/com/example/platform/jooq/"
            "tables/OperationInvocation.java",
            "package com.example.platform.jooq.tables; "
            "public final class OperationInvocation { String baseContentHash; }")))
    cases.append(("unbounded_registered_definition_dispatch", (
        "EXECUTABLE_OPERATION_DEFINITION_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId()",
            "registeredDefinition.definitionId()")))
    cases.append(("second_executable_operation_definition", (
        "EXECUTABLE_OPERATION_DEFINITION_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "if (request.definitionId().equals(OperationDefinition.V1.DELETE.definitionId()) "
            "&& request.definitionVersion().equals(OperationDefinition.V1.DELETE.version())) { "
            "throw new IllegalStateException(); }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("raw_string_failure_inference", (
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "if (failure.getMessage().contains(\"missing\")) { throw failure; }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("missing_base_content_hash_fails_open", (
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT",), replace_source(
            sources, "operation/operation/OperationRequest.java",
            "package com.example.platform.operation.operation; "
            "@org.springframework.modulith.NamedInterface(\"invocation\") "
            "public record OperationRequest(String baseContentHash) {}")))
    cases.append(("missing_base_hash_latest_fallback", (
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "if (request.baseContentHash() == null) { loadLatest(); }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("called_helper_direct_timeline_writer", (
        "H8_NEW_TIMELINE_WRITER_COUNT", "UNCLASSIFIED"), with_called_helper(
            sources, "CalledTimelineWriterHelper",
            "final class CalledTimelineWriterHelper { "
            "TimelineRevisionSaveService writer; "
            "void run(OperationRequest request) { writer.saveRevision(null); } }")))
    cases.append(("called_helper_timeline_ref_head_mutation", (
        "H8_NEW_HEAD_AUTHORITY_COUNT", "UNCLASSIFIED"), with_called_helper(
            sources, "CalledTimelineHeadHelper",
            "final class CalledTimelineHeadHelper { HeadUpdatePort head; "
            "void run(OperationRequest request) { head.updateHead(null); } }")))
    cases.append(("called_helper_operation_planner_plan_result_authority", (
        "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT",
        "DUPLICATE_OPERATION_INVOCATION_RESULT_AUTHORITY_COUNT", "UNCLASSIFIED"),
        with_called_helper(
            sources, "CalledPlanAuthorityHelper",
            "final class CalledPlanAuthorityHelper { OperationPlanner planner; "
            "OperationPlan plan; ApplyResult result; void run(OperationRequest request) {} "
            "record HelperOperationResult(ApplyResult result) {} }")))
    cases.append(("called_helper_registered_definition_dispatch", (
        "EXECUTABLE_OPERATION_DEFINITION_COUNT", "UNCLASSIFIED"), with_called_helper(
            sources, "CalledRegisteredDispatchHelper",
            "final class CalledRegisteredDispatchHelper { "
            "void run(OperationRequest request) { registeredDefinition.dispatch(request); } }")))
    cases.append(("called_helper_raw_message_inference", (
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT", "UNCLASSIFIED"), with_called_helper(
            sources, "CalledRawFailureHelper",
            "final class CalledRawFailureHelper { void run(OperationRequest request) { "
            "if (failure.getMessage().contains(\"missing\")) { throw failure; } } }")))
    cases.append(("called_helper_missing_hash_latest_fallback", (
        "MUTABLE_LATEST_FALLBACK_COUNT",
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT", "UNCLASSIFIED"),
        with_called_helper(
            sources, "CalledLatestFallbackHelper",
            "final class CalledLatestFallbackHelper { void run(OperationRequest request) { "
            "if (request.baseContentHash() == null) { loadLatest(); } } }")))
    cases.append(("workflow_fully_qualified_operation_plan", (
        "WORKFLOW_OPERATION_PLAN_IMPORT_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/FullyQualifiedPlanLeak.java",
            "package com.example.platform.workflow; final class FullyQualifiedPlanLeak { "
            "com.example.platform.operation.plan.OperationPlan plan; }")))
    cases.append(("workflow_fully_qualified_operation_planner", (
        "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/FullyQualifiedPlannerLeak.java",
            "package com.example.platform.workflow; final class FullyQualifiedPlannerLeak { "
            "com.example.platform.operation.plan.OperationPlanner planner; }")))
    cases.append(("workflow_fully_qualified_jooq", (
        "WORKFLOW_JOOQ_IMPORT_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/FullyQualifiedJooqLeak.java",
            "package com.example.platform.workflow; final class FullyQualifiedJooqLeak { "
            "org.jooq.DSLContext context; com.example.platform.jooq.tables.Records records; }")))
    cases.append(("canonical_service_public_controller_injection", (
        "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT", "UNCLASSIFIED"), with_source(
            sources,
            "platform-app/src/main/java/com/example/platform/web/PublicInvocationController.java",
            "package com.example.platform.web; @RestController public final class "
            "PublicInvocationController { CanonicalOperationInvocationService service; }")))
    cases.append(("timeline_service_public_controller_injection", (
        "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT", "UNCLASSIFIED"), with_source(
            sources,
            "platform-app/src/main/java/com/example/platform/web/PublicTimelineController.java",
            "package com.example.platform.web; @RestController public final class "
            "PublicTimelineController { TimelineMediaClipOperationService service; }")))
    cases.append(("generic_h8_schema_mutation", (
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT",), with_source(
            sources,
            "platform-app/src/main/resources/db/migration/V999__h8_runtime.sql",
            "create table h8_runtime_job (id bigint primary key);")))
    cases.append(("generic_h8_generated_jooq_type", (
        "H8_JOOQ_GENERATED_MUTATION_AUTHORITY_COUNT",), with_source(
            sources,
            "platform-app/build/generated-src/jooq/main/com/example/platform/jooq/"
            "tables/H8RuntimeJob.java",
            "package com.example.platform.jooq.tables; public final class H8RuntimeJob {}")))
    cases.append(("new_unclassified_h8_runtime_source", ("UNCLASSIFIED",), with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/runtime/H8InvocationRuntime.java",
        "package com.example.platform.render.runtime; final class H8InvocationRuntime { "
        "OperationInvocationContext context; }")))

    old_case_count = len(cases)
    cases.append(("fqcn_workflow_to_operation_internal", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT",
        "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT"), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/FqOperationInternal.java",
            "package com.example.platform.workflow; public final class FqOperationInternal { "
            "void run() { com.example.platform.operation.operation.OperationRequestResolver"
            ".resolve(null, null); } }")))
    cases.append(("fqcn_workflow_to_timeline_writer", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT",
        "WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT"), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/FqTimelineWriter.java",
            "package com.example.platform.workflow; public final class FqTimelineWriter { "
            "com.example.platform.timeline.app.TimelineRevisionSaveService writer; "
            "void run() { writer.saveRevision(null); } }")))
    cases.append(("fqcn_called_helper_all_mechanics", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT", "H8_NEW_TIMELINE_WRITER_COUNT",
        "H8_NEW_HEAD_AUTHORITY_COUNT", "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT",
        "EXECUTABLE_OPERATION_DEFINITION_COUNT", "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",
        "MUTABLE_LATEST_FALLBACK_COUNT", "UNCLASSIFIED"),
        with_fully_qualified_called_helper(
            sources, "FqHostileHelper",
            "public final class FqHostileHelper { TimelineRevisionSaveService writer; "
            "HeadUpdatePort head; OperationPlanner planner; OperationPlan plan; ApplyResult result; "
            "public static void run(OperationRequest request) { writer.saveRevision(null); "
            "head.updateHead(null); registeredDefinition.dispatch(request); "
            "if (failure.getMessage().contains(\"missing\")) { loadLatest(); } } "
            "record HelperOperationResult(ApplyResult result) {} }")))
    cases.append(("fqcn_called_helper_known_name_spoof", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT", "UNCLASSIFIED"),
        with_fully_qualified_called_helper(
            sources, "MediaTime",
            "public final class MediaTime { public static void run(OperationRequest request) {} }")))
    cases.append(("imported_known_name_spoof_helper", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT", "UNCLASSIFIED"),
        with_imported_called_helper(
            sources, "MediaTime",
            "public final class MediaTime { public static void run(OperationRequest request) {} }")))
    cases.append(("imported_known_name_nested_InvocationReceipt", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT", "UNCLASSIFIED",
        "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
        with_imported_called_helper(
            sources, "MediaTime",
            "public final class MediaTime { public static void run(OperationRequest request) {} "
            "public record InvocationReceipt(String status, String revision, String digest) {} }")))
    cases.append(("imported_known_name_helper_all_forbidden_mechanics", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT", "H8_NEW_TIMELINE_WRITER_COUNT",
        "H8_NEW_HEAD_AUTHORITY_COUNT", "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT",
        "EXECUTABLE_OPERATION_DEFINITION_COUNT", "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",
        "MUTABLE_LATEST_FALLBACK_COUNT", "UNCLASSIFIED"),
        with_imported_called_helper(
            sources, "MediaTime",
            "public final class MediaTime { TimelineRevisionSaveService writer; "
            "HeadUpdatePort head; OperationPlanner planner; OperationPlan plan; ApplyResult result; "
            "public static void run(OperationRequest request) { writer.saveRevision(null); "
            "head.updateHead(null); registeredDefinition.dispatch(request); "
            "if (failure.getMessage().contains(\"missing\")) { loadLatest(); } } "
            "record HelperOperationResult(ApplyResult result) {} }")))
    for authority_name, declaration, law in (
            ("InvocationReceipt", "record", "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationOutcome", "record", "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationResponse", "interface", "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationResult", "record", "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationReply", "record", "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationProblem", "class", "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationError", "class", "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationFailure", "record", "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT"),
            ("InvocationFault", "record", "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT")):
        body = "{}" if declaration != "record" else "(String value) {}"
        cases.append((f"alternate_peer_{authority_name}", (law,), with_source(
            sources,
            f"workflow-module/src/main/java/com/example/platform/workflow/{authority_name}.java",
            f"package com.example.platform.workflow; public {declaration} "
            f"{authority_name}{body}")))
    cases.append(("structural_peer_invocation_result_record", (
        "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/InvocationCompletion.java",
            "package com.example.platform.workflow; public record InvocationCompletion("
            "String status, String revision, String digest) {}")))
    cases.append(("structural_peer_invocation_failure_record", (
        "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/InvocationRejection.java",
            "package com.example.platform.workflow; public record InvocationRejection("
            "String code, String reason, boolean retryable) {}")))
    cases.append(("pascalcase_schema_markers", (
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT", "CAMELCASE_SCHEMA_MARKER_ESCAPE_COUNT"), with_source(
            sources,
            "feature-module/src/main/resources/schema/OperationInvocation.sql",
            "create table OperationInvocation (id bigint); create table H8RuntimeJob (id bigint);")))
    cases.append(("lowercamel_schema_markers", (
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT", "CAMELCASE_SCHEMA_MARKER_ESCAPE_COUNT"), with_source(
            sources,
            "feature-module/src/main/resources/schema/operationInvocation.sql",
            "create table operationInvocation (id bigint); create table h8RuntimeJob (id bigint);")))
    cases.append(("marker_independent_central_schema_path_mutation", (
        "H8_SCHEMA_MUTATION_AUTHORITY_COUNT",), with_source(
            sources,
            CENTRAL_FLYWAY_ROOT + "V999__unrelated_control.sql",
            "create table unrelated_control (id bigint primary key);")))
    cases.append(("nullable_expected_plan_digest_bypass", (
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "if (!prepared.plan().planDigest().equals(expectedPlanDigest)) {",
            "if (expectedPlanDigest != null "
            "&& !prepared.plan().planDigest().equals(expectedPlanDigest)) {")))
    cases.append(("internal_null_expected_plan_digest", (
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "prepared.plan().planDigest(), context.invocationId(), context.actor()",
            "null, context.invocationId(), context.actor()")))
    cases.append(("wrong_digest_absorbed_by_assignment", (
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "throw new TimelineOperationException(TimelineOperationException.Code.PLAN_CHANGED,\n"
            "                    List.of(\"expected plan digest does not match freshly validated plan\"));",
            "expectedPlanDigest = prepared.plan().planDigest();")))
    cases.append(("prepare_internal_authorization_after_hydration", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, request);",
            "PreparedOperation prepared = prepare(tenantId, projectId, request);\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepared;")))
    cases.append(("public_preview_prepare_before_authorization", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();",
            "prepare(tenantId, projectId, toOperationRequest(projectId, command));\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();")))
    cases.append(("public_apply_prepare_before_authorization", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        PreparedOperation prepared = prepare(",
            "prepare(tenantId, projectId, toOperationRequest(projectId, command));\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        PreparedOperation prepared = prepare(")))
    cases.append(("internal_findById_before_authorization", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, request);",
            "revisionSaveService.findById(tenantId, request.baseRevisionId());\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, request);")))
    cases.append(("authorization_helper_contains_hydration", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "Objects.requireNonNull(actor, \"actor\");\n"
            "        if (!Objects.equals(tenantId, actor.tenantId())) {",
            "Objects.requireNonNull(actor, \"actor\");\n"
            "        revisionSaveService.findById(tenantId, projectId);\n"
            "        if (!Objects.equals(tenantId, actor.tenantId())) {")))
    cases.append(("dead_authorization_anchor_before_prepare", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();",
            "if (false) {\n"
            "            requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        }\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();")))
    cases.append(("duplicate_authorization_after_prepare", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();",
            "prepare(tenantId, projectId, toOperationRequest(projectId, command));\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);\n"
            "        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();")))
    for authority_name in ("InvocationAcknowledgement", "InvocationDenial"):
        cases.append((f"alternate_peer_{authority_name}", (
            "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT",
            "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT"), with_source(
                sources,
                f"workflow-module/src/main/java/com/example/platform/workflow/"
                f"{authority_name}.java",
                f"package com.example.platform.workflow; public record {authority_name}("
                + ("String state, String version, String checksum) {}"
                   if authority_name == "InvocationAcknowledgement"
                   else "String errno, String explanation, long backoffMillis) {}"))))
    for authority_name in ("InvocationAcknowledgement", "InvocationDenial"):
        cases.append((f"imported_known_helper_nested_{authority_name}", (
            "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT",
            "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT"), replace_once(
                sources, "shared/time/MediaTime.java",
                "    public record MediaTimeSchema(long ticks, long timeScale, "
                "String schemaVersion) {}\n}",
                "    public record MediaTimeSchema(long ticks, long timeScale, "
                "String schemaVersion) {}\n\n"
                f"    public record {authority_name}("
                + ("String state, String version, String checksum) {}\n}"
                   if authority_name == "InvocationAcknowledgement"
                   else "String errno, String explanation, long backoffMillis) {}\n}"))))

    cases.append(("authorization_denial_return_bypass", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "throw new TimelineOperationException(\n"
            "                    TimelineOperationException.Code.AUTHORIZATION_DENIED,\n"
            "                    List.of(\"project operation access denied\"));",
            "return;")))
    cases.append(("authorization_denial_swallowed_catch", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "throw new TimelineOperationException(\n"
            "                    TimelineOperationException.Code.AUTHORIZATION_DENIED,\n"
            "                    List.of(\"project operation access denied\"));",
            "try {\n"
            "                throw new TimelineOperationException(\n"
            "                        TimelineOperationException.Code.AUTHORIZATION_DENIED,\n"
            "                        List.of(\"project operation access denied\"));\n"
            "            } catch (TimelineOperationException ignored) {\n"
            "                // denial swallowed\n"
            "            }")))
    cases.append(("digest_throw_swallowed_catch", (
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "throw new TimelineOperationException(TimelineOperationException.Code.PLAN_CHANGED,\n"
            "                    List.of(\"expected plan digest does not match freshly validated plan\"));",
            "try {\n"
            "                throw new TimelineOperationException(\n"
            "                        TimelineOperationException.Code.PLAN_CHANGED,\n"
            "                        List.of(\"expected plan digest does not match freshly validated plan\"));\n"
            "            } catch (TimelineOperationException ignored) {\n"
            "                // mismatch swallowed\n"
            "            }")))
    cases.append(("dead_digest_check", (
        "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "if (!prepared.plan().planDigest().equals(expectedPlanDigest)) {\n"
            "            throw new TimelineOperationException(TimelineOperationException.Code.PLAN_CHANGED,\n"
            "                    List.of(\"expected plan digest does not match freshly validated plan\"));\n"
            "        }",
            "if (false) {\n"
            "            if (!prepared.plan().planDigest().equals(expectedPlanDigest)) {\n"
            "                throw new TimelineOperationException(\n"
            "                        TimelineOperationException.Code.PLAN_CHANGED,\n"
            "                        List.of(\"expected plan digest does not match freshly validated plan\"));\n"
            "            }\n"
            "        }")))
    cases.append(("unicode_fqcn_and_invocation_authority_escape", (
        "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT",
        "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT",
        "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT"), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/UnicodeEscape.java",
            "pa\\u0063kage com.example.platform.workflow; public final class UnicodeEscape { "
            "void run() { com.example.platform.operation.\\uuuu006fperation."
            "OperationRequestResolver.resolve(null, null); } "
            "public record Invoc\\u0061tionReceipt(String state, String digest) {} }")))
    cases.append(("alternate_registry_dispatch", (
        "EXECUTABLE_OPERATION_DEFINITION_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "var executable = OperationDefinition.V1.ALL.stream()\n"
            "                    .filter(definition -> definition.capabilities()\n"
            "                            .contains(request.definitionId()))\n"
            "                    .findFirst().orElseThrow();\n"
            "            executable.dispatch(request);\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("throwable_toString_failure_inference", (
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "try { inspectFailureSource(); } catch (Throwable failure) {\n"
            "                String inferred = failure.toString();\n"
            "                if (inferred.contains(\"missing\")) { throw new RuntimeException(); }\n"
            "            }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("findNewest_mutable_latest_fallback", (
        "MUTABLE_LATEST_FALLBACK_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "findNewestTimelineRevision();\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("invalid_unicode_escape_fails_closed", ("UNCLASSIFIED",), with_source(
        sources,
        "render-module/src/main/java/com/example/platform/render/app/operation/"
        "InvalidUnicodeEscape.java",
        "package com.example.platform.render.app.operation; "
        "final class InvalidUnicodeEscape { \\u12G4 }")))
    cases.append(("workflow_operation_request_structural_peer", (
        "NEW_OPERATION_REQUEST_PEER_TYPE_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/"
            "WorkflowOperationRequest.java",
            "package com.example.platform.workflow; public final class "
            "WorkflowOperationRequest { Object definitionId; Object version; "
            "Object target; Object parameters; String baseRevisionId; "
            "String baseContentHash; }")))
    cases.append(("compound_request_actor_identifier", (
        "REQUEST_CONTROLLED_ACTOR_AUTHORITY_COUNT",), replace_once(
            sources, "operation/operation/OperationRequest.java",
            "String baseContentHash,\n        String requestMetadata",
            "String baseContentHash,\n        String effectiveActorId,\n"
            "        String requestMetadata")))
    cases.append(("base_hash_null_and_blank_bug", (
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT",), replace_once(
            sources, "operation/operation/OperationRequest.java",
            "if (baseContentHash == null || baseContentHash.isBlank()) {",
            "if (baseContentHash == null && baseContentHash.isBlank()) {")))
    cases.append(("id_version_and_bug", (
        "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "equals(request.definitionId())\n"
            "                || !OperationDefinition.V1.ADD_MEDIA_CLIP.version()",
            "equals(request.definitionId())\n"
            "                && !OperationDefinition.V1.ADD_MEDIA_CLIP.version()")))
    cases.append(("map_definition_dispatch", (
        "EXECUTABLE_OPERATION_DEFINITION_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "Map<String, Runnable> routes = Map.of();\n"
            "            routes.get(request.definitionId().value()).run();\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("constant_true_authorization_binding", (
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "AuthorizationDecision boundDecision = securityDecision.allowed()",
            "AuthorizationDecision boundDecision = true")))
    cases.append(("subtype_exception_toString", (
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT",), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "try { inspectFailureSource(); } catch (IllegalStateException problem) {\n"
            "                String inferred = problem.toString();\n"
            "                if (inferred.contains(\"missing\")) { "
            "throw new RuntimeException(); }\n"
            "            }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("descending_sort_findFirst_latest", (
        "MUTABLE_LATEST_FALLBACK_COUNT",), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "var securityDecision = authorizationPort.decide(new AuthorizationRequest(",
            "List<TimelineRevision> candidates = List.of(\n"
            "                revisionSaveService.findById(\n"
            "                        tenantId, prepared.plan().baseRevisionId()));\n"
            "        candidates.stream()\n"
            "                .sorted(java.util.Comparator\n"
            "                        .comparing(TimelineRevision::revisionId)\n"
            "                        .reversed())\n"
            "                .findFirst();\n\n"
            "        var securityDecision = authorizationPort.decide(new AuthorizationRequest(")))

    # V6 hostile-review shapes are independently reintroduced against the
    # frozen source map. Hash attestation is mandatory for every shape; the
    # existing semantic law remains a defense-in-depth expectation where the
    # mutation has an existing semantic classification.
    cases.append(("v6_canonical_actor_authority_field", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",), replace_once(
            sources, "shared/authorization/CanonicalActor.java",
            "Set<String> roles,\n        String authSource)",
            "Set<String> roles,\n        boolean platformAdmin,\n        String authSource)")))
    cases.append(("v6_aliased_map_definition_dispatch", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "EXECUTABLE_OPERATION_DEFINITION_COUNT"), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "Map<String, Runnable> dispatchers = Map.of();\n"
            "            String dispatchKey = request.definitionId().value();\n"
            "            Runnable selected = dispatchers.get(dispatchKey);\n"
            "            selected.run();\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("v6_security_decision_reassignment", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT"), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "AuthorizationDecision boundDecision = securityDecision.allowed()",
            "securityDecision = securityDecision.allowed() ? securityDecision : securityDecision;\n"
            "        AuthorizationDecision boundDecision = securityDecision.allowed()")))
    cases.append(("v6_symmetric_exception_string_comparison", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT"), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "var outcome = mediaClipService.invoke(request, context);",
            "try { inspectFailureSource(); } catch (IllegalStateException problem) {\n"
            "                if (\"missing\".equals(problem.toString())) { "
            "throw new RuntimeException(); }\n"
            "            }\n"
            "            var outcome = mediaClipService.invoke(request, context);")))
    cases.append(("v6_split_descending_sort_get_zero", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "MUTABLE_LATEST_FALLBACK_COUNT"), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "var securityDecision = authorizationPort.decide(new AuthorizationRequest(",
            "List<TimelineRevision> candidates = List.of(\n"
            "                revisionSaveService.findById(\n"
            "                        tenantId, prepared.plan().baseRevisionId()));\n"
            "        java.util.Comparator<TimelineRevision> descendingRevision =\n"
            "                java.util.Comparator.comparing(TimelineRevision::revisionId)\n"
            "                        .reversed();\n"
            "        candidates.sort(descendingRevision);\n"
            "        TimelineRevision inferredHead = candidates.get(0);\n\n"
            "        var securityDecision = authorizationPort.decide(new AuthorizationRequest(")))
    cases.append(("v6_null_and_blank_base_hash", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT"), replace_once(
            sources, "operation/operation/OperationRequest.java",
            "if (baseContentHash == null || baseContentHash.isBlank()) {",
            "if (baseContentHash == null && baseContentHash.isBlank()) {")))
    cases.append(("v6_definition_id_and_version", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT"), replace_once(
            sources, "CanonicalOperationInvocationService.java",
            "equals(request.definitionId())\n"
            "                || !OperationDefinition.V1.ADD_MEDIA_CLIP.version()",
            "equals(request.definitionId())\n"
            "                && !OperationDefinition.V1.ADD_MEDIA_CLIP.version()")))
    cases.append(("v6_constant_true_authorization", (
        "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT",
        "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT"), replace_once(
            sources, "TimelineMediaClipOperationService.java",
            "AuthorizationDecision boundDecision = securityDecision.allowed()",
            "AuthorizationDecision boundDecision = true")))

    cases.append(("workflow_call_receipt_structural_result", (
        "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/WorkflowCallReceipt.java",
            "package com.example.platform.workflow; public record WorkflowCallReceipt("
            "String definitionId, String definitionVersion, String planDigest, "
            "String revisionId, String status) {}")))
    cases.append(("workflow_call_denial_structural_failure", (
        "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT",), with_source(
            sources,
            "workflow-module/src/main/java/com/example/platform/workflow/WorkflowCallDenial.java",
            "package com.example.platform.workflow; public record WorkflowCallDenial("
            "String definitionId, String version, String failureCode, "
            "String explanation, long backoffMillis) {}")))

    # V7 candidate-boundary controls: these preexisting transitive authorities
    # are intentionally outside the authorized H8 change universe. Their
    # contents need not be hashed to make changing them fail closed.
    scope_law = ("H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT",)
    cases.append(("changed_parameter_digest", scope_law, append_to_existing_source(
        sources,
        "operation-module/src/main/java/com/example/platform/operation/operation/ParameterDigest.java",
        "\n// hostile changed-path control\n")))
    cases.append(("changed_rbac_authorization_decision_port", scope_law,
                  append_to_existing_source(
                      sources,
                      "identity-access-module/src/main/java/com/example/platform/identity/app/"
                      "RbacAuthorizationDecisionPort.java",
                      "\n// hostile changed-path control\n")))
    cases.append(("changed_default_timeline_revision_persistence", scope_law,
                  append_to_existing_source(
                      sources,
                      "timeline-module/src/main/java/com/example/platform/timeline/app/"
                      "DefaultTimelineRevisionPersistence.java",
                      "\n// hostile changed-path control\n")))
    cases.append(("changed_timeline_revision_semantic_context_json_codec", scope_law,
                  append_to_existing_source(
                      sources,
                      "timeline-module/src/main/java/com/example/platform/timeline/version/"
                      "TimelineRevisionSemanticContextJsonCodec.java",
                      "\n// hostile changed-path control\n")))
    cases.append(("new_primary_permit_all_decision_port", scope_law, with_source(
        sources,
        "identity-access-module/src/main/java/com/example/platform/identity/app/"
        "PermitAllDecisionPort.java",
        "package com.example.platform.identity.app; "
        "import com.example.platform.shared.authorization.AuthorizationDecision; "
        "import com.example.platform.shared.authorization.AuthorizationDecisionPort; "
        "import com.example.platform.shared.authorization.AuthorizationRequest; "
        "import org.springframework.context.annotation.Primary; "
        "@Primary final class PermitAllDecisionPort implements AuthorizationDecisionPort { "
        "public AuthorizationDecision decide(AuthorizationRequest request) { "
        "return AuthorizationDecision.allow(\"permit-all\"); } }")))
    cases.append(("changed_unrelated_production_source", scope_law,
                  append_to_existing_source(
                      sources,
                      "shared-kernel/src/main/java/com/example/platform/shared/Ids.java",
                      "\n// hostile changed-path control\n")))

    passing_controls: list[tuple[str, dict[str, str]]] = []
    passing_controls.append(("workflow_unrelated_operation_request_dto", with_source(
        sources,
        "workflow-module/src/main/java/com/example/platform/workflow/OperationRequest.java",
        "package com.example.platform.workflow; "
        "public record OperationRequest(String displayLabel) {}")))
    passing_controls.append(("workflow_unrelated_process_receipt_dto", with_source(
        sources,
        "workflow-module/src/main/java/com/example/platform/workflow/WorkflowProcessReceipt.java",
        "package com.example.platform.workflow; public record WorkflowProcessReceipt("
        "String definitionId, String version, String processDigest, String status) {}")))
    passing_controls.append(("workflow_unrelated_process_denial_dto", with_source(
        sources,
        "workflow-module/src/main/java/com/example/platform/workflow/WorkflowProcessDenial.java",
        "package com.example.platform.workflow; public record WorkflowProcessDenial("
        "String processId, String code, String message, boolean retryable) {}")))
    passing_controls.append(("unrelated_unchanged_source_absent_from_changed_paths", with_source(
        sources,
        "catalog-module/src/main/java/com/example/platform/catalog/UnchangedCatalogProjection.java",
        "package com.example.platform.catalog; "
        "final class UnchangedCatalogProjection { String displayLabel; }")))

    old_failures = 0
    new_failures = 0
    for index, (name, laws, mutated) in enumerate(cases):
        result = evaluate(
            mutated,
            changed_paths=changed_paths | mutated_source_paths(sources, mutated))
        detected = all(
            result.counts[law] != (1 if law in ONE_LAWS else 0)
            for law in laws)
        print(f"H8_MUTATION {name}={'PASS' if detected else 'FAIL'} {'/'.join(laws)}")
        if index < old_case_count:
            old_failures += int(not detected)
        else:
            new_failures += int(not detected)
    for name, control_sources in passing_controls:
        # These controls model preexisting, unchanged source: source presence
        # alone is not a candidate change and must not be over-banned.
        result = evaluate(control_sources, changed_paths=changed_paths)
        detected = result.passed
        print(f"H8_MUTATION {name}={'PASS' if detected else 'FAIL'} BASELINE_PASS")
        new_failures += int(not detected)

    lifecycle_total, lifecycle_failures = run_scope_lifecycle_controls(
        root, sources, changed_paths)
    new_failures += lifecycle_failures

    governed_hash_failures = 0
    for index, path in enumerate(sorted(GOVERNED_RUNTIME_SOURCE_SHA256), start=1):
        mutated = dict(sources)
        mutated[path] = mutated.get(path, "") + "\n// governed-runtime-hash-mutation\n"
        mismatch_count, _ = governed_runtime_hash_mismatches(mutated)
        detected = mismatch_count == 1
        print("H8_MUTATION governed_runtime_source_hash_"
              f"{index:03d}={'PASS' if detected else 'FAIL'} "
              "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT")
        governed_hash_failures += int(not detected)
    all_governed_hashes_detected = governed_hash_failures == 0
    print("H8_MUTATION governed_runtime_source_hash_all_files="
          f"{'PASS' if all_governed_hashes_detected else 'FAIL'} "
          "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT")
    new_failures += governed_hash_failures
    removed_rule_order = tuple(
        law for law in LAW_ORDER if law != "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT")
    mutation_counts = dict(baseline.counts)
    mutation_counts["RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT"] = 1
    remaining_laws_would_miss_mutation = all(
        mutation_counts[law] == (1 if law in ONE_LAWS else 0)
        for law in removed_rule_order)
    runtime_validation_detected = (
        remaining_laws_would_miss_mutation
        and bool(law_definition_errors(removed_rule_order))
        and not evaluation_passes(mutation_counts, removed_rule_order))
    print("H8_MUTATION guard_rule_removed_runtime_validation="
          f"{'PASS' if runtime_validation_detected else 'FAIL'} LAW_DEFINITION_VALIDATION")
    old_failures += int(not runtime_validation_detected)
    failures = old_failures + new_failures
    mutation_matrix_total = (
        len(cases) + len(passing_controls)
        + len(GOVERNED_RUNTIME_SOURCE_SHA256) + lifecycle_total + 2)
    print(f"OLD_H8_MUTATION_REGRESSION_COUNT={old_failures}")
    print(f"NEW_H8_HOSTILE_MUTATION_FAILURES={new_failures}")
    print(f"H8_MUTATION_MATRIX_TOTAL={mutation_matrix_total}")
    print(f"H8_MUTATION_MATRIX_FAILURES={failures}")
    return failures == 0


def print_evaluation(result: Evaluation) -> None:
    for name in LAW_ORDER:
        print(f"{name}={result.counts.get(name, -1)}")
    for detail in result.details:
        print(f"H8_GUARD_DETAIL={detail}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    root = args.root.resolve()
    sources, errors, changed_paths, current_head_descendant = sources_at(root)
    print(f"H8_SCOPE_ATTESTATION_BASE_SHA={H8_PRE_CANONICAL_BASE_SHA}")
    print(f"H8_SCOPE_ATTESTATION_ACCEPTED_SHA={H8_ACCEPTED_CANONICAL_SHA}")
    print("H8_SCOPE_ATTESTATION_CURRENT_HEAD_DESCENDANT="
          f"{'PASS' if current_head_descendant else 'FAIL'}")
    print(f"H8_HISTORICAL_CHANGED_PATH_COUNT={len(changed_paths)}")
    result = evaluate(sources, errors, changed_paths)
    print_evaluation(result)
    self_test_passed = True
    if args.self_test:
        try:
            self_test_passed = run_self_test(root, sources, changed_paths)
        except (RuntimeError, ValueError) as failure:
            print("H8_MUTATION_MATRIX_TOTAL=0")
            print("H8_MUTATION_MATRIX_FAILURES=1")
            print(f"H8_SELF_TEST_ERROR={failure}", file=sys.stderr)
            self_test_passed = False
    if result.passed and self_test_passed:
        print("H8_OPERATION_INVOCATION_BOUNDARY_GUARD=PASS")
        return 0
    print("H8_OPERATION_INVOCATION_BOUNDARY_GUARD=FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
