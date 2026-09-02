declare const brand: unique symbol

type Brand<T, Name extends string> = T & { readonly [brand]: Name }

export type RevisionId = Brand<string, 'RevisionId'>
export type ContentHash = Brand<string, 'ContentHash'>
export type TimelineId = Brand<string, 'TimelineId'>
export type ProjectId = Brand<string, 'ProjectId'>
export type TrackId = Brand<string, 'TrackId'>
export type ClipId = Brand<string, 'ClipId'>
export type MediaAssetId = Brand<string, 'MediaAssetId'>
export type MediaStreamId = Brand<string, 'MediaStreamId'>
export type ArtifactId = Brand<string, 'ArtifactId'>
export type OperationDefinitionId = Brand<string, 'OperationDefinitionId'>
export type PlanDigest = Brand<string, 'PlanDigest'>
export type ApplyCommandId = Brand<string, 'ApplyCommandId'>
export type ExactMediaTime = Brand<string, 'ExactMediaTime'>

const ID_PATTERN = /^[A-Za-z0-9._:-]+$/
const DIGEST_PATTERN = /^[0-9a-fA-F]{64}$/
const RATIONAL_PATTERN = /^[+-]?[0-9]+(?:\/[1-9][0-9]*)?$/

function boundedId<Name extends string>(value: string, label: string): Brand<string, Name> {
  if (!value || value.length > 64 || !ID_PATTERN.test(value)) {
    throw new Error(`${label} must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen.`)
  }
  return value as Brand<string, Name>
}

function digest<Name extends string>(value: string, label: string): Brand<string, Name> {
  if (!DIGEST_PATTERN.test(value)) throw new Error(`${label} must be an exact 64-character hexadecimal digest.`)
  return value.toLowerCase() as Brand<string, Name>
}

export const revisionId = (value: string) => boundedId<'RevisionId'>(value, 'Revision ID')
export const timelineId = (value: string) => boundedId<'TimelineId'>(value, 'Timeline ID')
export const projectId = (value: string) => boundedId<'ProjectId'>(value, 'Project ID')
export const trackId = (value: string) => boundedId<'TrackId'>(value, 'Track ID')
export const clipId = (value: string) => boundedId<'ClipId'>(value, 'Clip ID')
export const mediaAssetId = (value: string) => boundedId<'MediaAssetId'>(value, 'Media Asset ID')
export const mediaStreamId = (value: string) => boundedId<'MediaStreamId'>(value, 'Media Stream ID')
export const artifactId = (value: string) => boundedId<'ArtifactId'>(value, 'Artifact ID')
export const operationDefinitionId = (value: string) => boundedId<'OperationDefinitionId'>(value, 'Operation Definition ID')
export const applyCommandId = (value: string) => boundedId<'ApplyCommandId'>(value, 'Apply Command ID')
export const contentHash = (value: string) => digest<'ContentHash'>(value, 'Content hash')
export const planDigest = (value: string) => digest<'PlanDigest'>(value, 'Plan digest')

export function exactMediaTime(value: string): ExactMediaTime {
  if (!value || value.length > 64 || !RATIONAL_PATTERN.test(value)) {
    throw new Error('Media time must be an exact integer or rational string with a positive denominator.')
  }
  return value as ExactMediaTime
}

export function isContentHash(value: string): boolean {
  return DIGEST_PATTERN.test(value)
}

export function isExactMediaTime(value: string): boolean {
  return value.length <= 64 && RATIONAL_PATTERN.test(value)
}

export function isBoundedId(value: string): boolean {
  return value.length > 0 && value.length <= 64 && ID_PATTERN.test(value)
}

export const ADD_MEDIA_CLIP_DEFINITION = operationDefinitionId('timeline.media-clip.add')
export const ADD_MEDIA_CLIP_PRESENTATION_VERSION = '1.0' as const
export const ADD_MEDIA_CLIP_PREVIEW_OPERATION = 'ADD_MEDIA_CLIP_V1' as const
