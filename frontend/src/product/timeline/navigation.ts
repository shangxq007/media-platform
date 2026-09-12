import { z } from 'zod'
import type { EffectiveAccessEntry } from '../../foundation/effectiveAccess'
import type { CanonicalHeadReference } from './gateways'
import { exactMediaTime, isBoundedId, isContentHash, isExactMediaTime } from './types'

// Frontend-local verification contract only. TimelineQueryGateway has no geometry
// read method. No endpoint, server DTO, permission or real adapter is established.
export type NavigationTarget = Omit<CanonicalHeadReference, 'contentHash'> & { readonly contentHash?: CanonicalHeadReference['contentHash'] }
export interface NavigationScope {
  readonly principalId: string
  readonly tenantId: string
  readonly sessionId: string
  readonly workspaceId: string
  readonly projectId: string
}
export interface NavigationRequest { readonly scope: NavigationScope; readonly target: NavigationTarget; readonly requestId: string }
export interface TimelineNavigationSource {
  readonly scope: NavigationScope
  readonly access: EffectiveAccessEntry
  readonly accessBinding: { readonly kind: 'TEST_ONLY_UNAGREED'; readonly key: string }
  readonly adapter: {
    readonly origin: 'isolated-verification'
    read(request: NavigationRequest, signal: AbortSignal): Promise<unknown>
  }
}

function rational(value: string): readonly [bigint, bigint] {
  exactMediaTime(value)
  const [n, d = '1'] = value.split('/')
  return [BigInt(n), BigInt(d)]
}
export function compareTime(a: string, b: string): number {
  const [an, ad] = rational(a), [bn, bd] = rational(b)
  const difference = an * bd - bn * ad
  return difference < 0n ? -1 : difference > 0n ? 1 : 0
}
export function timeDifference(end: string, start: string): string {
  const [en, ed] = rational(end), [sn, sd] = rational(start)
  const numerator = en * sd - sn * ed, denominator = ed * sd
  let a = numerator < 0n ? -numerator : numerator, b = denominator
  while (b) { const remainder = a % b; a = b; b = remainder }
  return `${numerator / a}/${denominator / a}`
}
const id = z.string().refine(isBoundedId)
const name = z.string().max(1000)
const time = z.string().refine(isExactMediaTime).refine(value => isExactMediaTime(value) && compareTime(value, '0') >= 0)
const range = z.object({ start: time, end: time }).strict().refine(value => compareTime(value.start, value.end) <= 0)
const scopeSchema = z.object({ principalId: id, tenantId: id, sessionId: id, workspaceId: id, projectId: id }).strict()
const targetSchema = z.object({ projectId: id, timelineId: id, revisionId: id, contentHash: z.string().refine(isContentHash).optional() }).strict()
const clipSchema = z.object({
  id, trackId: id, name: name.optional(), type: name.optional(), version: id.optional(),
  timelineRange: range.optional(), sourceRange: range.optional(),
  source: z.object({ mediaAssetId: id, mediaStreamId: id.optional(), artifactId: id.optional(), contentDigest: z.string().refine(isContentHash).optional() }).strict().optional(),
}).strict()
const trackSchema = z.object({ id, name: name.optional(), type: name.optional(), clips: z.array(clipSchema).max(500) }).strict()
const receipt = { requestId: id, scope: scopeSchema, target: targetSchema }
const snapshotSchema = z.discriminatedUnion('status', [
  z.object({ ...receipt, status: z.literal('ok'), completeness: z.enum(['complete', 'bounded']), timeBasis: z.enum(['exact-seconds', 'unknown']), bounds: range.optional(), tracks: z.array(trackSchema).max(100) }).strict(),
  z.object({ ...receipt, status: z.enum(['unavailable', 'error', 'stale', 'restricted']) }).strict(),
])
export type NavigationSnapshot = Extract<z.infer<typeof snapshotSchema>, { status: 'ok' }>
export type NavigationClip = z.infer<typeof clipSchema>
export type NavigationTrack = z.infer<typeof trackSchema>
export function targetKey(target: NavigationTarget | null): string {
  return JSON.stringify(target && [target.projectId, target.timelineId, target.revisionId, target.contentHash ?? null])
}
export function scopeKey(scope: NavigationScope): string {
  return JSON.stringify([scope.principalId, scope.tenantId, scope.sessionId, scope.workspaceId, scope.projectId])
}
export function accessKey(source: TimelineNavigationSource): string {
  const { access: a } = source
  return JSON.stringify([source.accessBinding.kind, source.accessBinding.key, a.key, a.source, a.status, a.reasonCode, a.factors.capability, a.factors.runtime, a.factors.entitlement, a.factors.policy, a.factors.quota])
}
export function navigationAccess(source: TimelineNavigationSource): boolean {
  return scopeSchema.safeParse(source.scope).success && source.adapter.origin === 'isolated-verification'
    && source.accessBinding.kind === 'TEST_ONLY_UNAGREED' && Boolean(source.accessBinding.key)
    && source.accessBinding.key === source.access.key && source.access.source === 'SERVER' && source.access.status === 'AVAILABLE'
    && Object.values(source.access.factors).every(factor => factor === 'SATISFIED' || factor === 'NOT_APPLICABLE')
}
export function parseNavigationSnapshot(input: unknown, request: NavigationRequest) {
  const value = snapshotSchema.parse(input)
  if (value.requestId !== request.requestId || scopeKey(value.scope) !== scopeKey(request.scope)
    || targetKey(value.target as NavigationTarget) !== targetKey(request.target)) throw new Error('Navigation receipt mismatch')
  if (value.status !== 'ok') return value
  const tracks = new Set<string>(), clips = new Set<string>()
  for (const track of value.tracks) {
    if (tracks.has(track.id)) throw new Error('Duplicate track')
    tracks.add(track.id)
    for (const clip of track.clips) {
      if (clips.has(clip.id) || clip.trackId !== track.id || clips.size >= 500) throw new Error('Invalid clip relationship or bound')
      clips.add(clip.id)
      if (value.bounds && clip.timelineRange && (compareTime(clip.timelineRange.start, value.bounds.start) < 0 || compareTime(clip.timelineRange.end, value.bounds.end) > 0)) throw new Error('Clip outside supplied bounds')
    }
  }
  return value
}
export const selectionId = (kind: 'track' | 'clip', id: string) => `${kind}:${id}`
// Reuse authored MediaClip.TimeRange.contains: both endpoints are included.
// RenderExtent/RenderSampleWindow have different execution semantics, not used here.
export function clipsAtTime(snapshot: NavigationSnapshot, position: string): NavigationClip[] {
  if (snapshot.timeBasis !== 'exact-seconds') return []
  return snapshot.tracks.flatMap(track => track.clips).filter(clip => clip.timelineRange
    && compareTime(position, clip.timelineRange.start) >= 0 && compareTime(position, clip.timelineRange.end) <= 0)
}
