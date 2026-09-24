# Artifact authority map (2026-09-25)

| Concern | Sole authority | Retired or excluded authority |
|---|---|---|
| Artifact identity, scope, kind, digest, lifecycle | `artifact` / Artifact module | MediaAsset id and repository |
| Media technical facts | `artifact_media_details` keyed by `artifact_id` | media_asset columns as canonical facts |
| Bytes and object placement | Storage object store and Artifact replica facts | MediaAsset storage key |
| Lineage and provenance | Artifact relations and Artifact provenance | MediaAsset-artifact link as a result store |
| Conversion declaration | Conversion Specification and platform Conversion Contract | Provider-defined public types |
| Authorization and retrieval | Artifact scope checks and Artifact API | MediaAsset authorization/routes |

V18 preserves legacy rows before retiring the MediaAsset table name. The renamed
relations are historical migration inputs and are protected against mutation;
they do not grant identity, lifecycle, authorization, retrieval or result
authority.
