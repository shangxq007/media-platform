export type ReferenceKind =
  | 'PROJECT'
  | 'MEDIA_ASSET'
  | 'ARTIFACT'
  | 'TIMELINE'
  | 'REVISION'
  | 'RENDER'
  | 'WORKFLOW'

interface Reference<K extends ReferenceKind> {
  readonly kind: K
  readonly id: string
  readonly label?: string
}

export type ProjectRef = Reference<'PROJECT'>
export type MediaAssetRef = Reference<'MEDIA_ASSET'>
export type ArtifactRef = Reference<'ARTIFACT'>
export type TimelineRef = Reference<'TIMELINE'>
export type RevisionRef = Reference<'REVISION'>
export type RenderRef = Reference<'RENDER'>
export type WorkflowRef = Reference<'WORKFLOW'>

export type CrossSurfaceReference =
  | ProjectRef
  | MediaAssetRef
  | ArtifactRef
  | TimelineRef
  | RevisionRef
  | RenderRef
  | WorkflowRef

export interface CrossSurfaceSelection {
  readonly primary: CrossSurfaceReference | null
  readonly related: readonly CrossSurfaceReference[]
  readonly revision: RevisionRef | null
}

export const emptySelection: CrossSurfaceSelection = {
  primary: null,
  related: [],
  revision: null,
}

export function isReferenceCompatible(
  reference: CrossSurfaceReference,
  acceptedKinds: readonly ReferenceKind[],
): boolean {
  return acceptedKinds.includes(reference.kind)
}

export function retainCompatibleSelection(
  selection: CrossSurfaceSelection,
  acceptedKinds: readonly ReferenceKind[],
): CrossSurfaceSelection {
  return {
    primary:
      selection.primary && isReferenceCompatible(selection.primary, acceptedKinds)
        ? selection.primary
        : null,
    related: selection.related.filter(reference =>
      isReferenceCompatible(reference, acceptedKinds),
    ),
    revision: acceptedKinds.includes('REVISION') ? selection.revision : null,
  }
}
