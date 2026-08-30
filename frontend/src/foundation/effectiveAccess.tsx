import type { ReactNode } from 'react'

export type EffectiveAccessStatus =
  | 'AVAILABLE'
  | 'UNAVAILABLE'
  | 'NOT_ENTITLED'
  | 'POLICY_DENIED'
  | 'QUOTA_EXHAUSTED'
  | 'RUNTIME_UNAVAILABLE'
  | 'UNKNOWN_FAIL_CLOSED'

export type EffectiveAccessFactorStatus =
  | 'SATISFIED'
  | 'UNSATISFIED'
  | 'UNKNOWN'
  | 'NOT_APPLICABLE'

export interface EffectiveAccessFactors {
  readonly capability: EffectiveAccessFactorStatus
  readonly runtime: EffectiveAccessFactorStatus
  readonly entitlement: EffectiveAccessFactorStatus
  readonly policy: EffectiveAccessFactorStatus
  readonly quota: EffectiveAccessFactorStatus
}

export interface EffectiveAccessEntry {
  readonly key: string
  readonly status: EffectiveAccessStatus
  readonly reasonCode: string
  readonly explanation: string
  readonly factors: EffectiveAccessFactors
  readonly source: 'SERVER' | 'MISSING_SERVER_PROJECTION' | 'DEVELOPMENT_FAIL_CLOSED'
  readonly observedAt?: string
}

export type EffectiveAccessCatalog = Readonly<Record<string, EffectiveAccessEntry>>

export function unknownAccess(key: string): EffectiveAccessEntry {
  return {
    key,
    status: 'UNKNOWN_FAIL_CLOSED',
    reasonCode: 'EFFECTIVE_ACCESS_PROJECTION_MISSING',
    explanation: 'Effective access is not available from the server. This action is disabled.',
    factors: {
      capability: 'UNKNOWN',
      runtime: 'UNKNOWN',
      entitlement: 'UNKNOWN',
      policy: 'UNKNOWN',
      quota: 'UNKNOWN',
    },
    source: import.meta.env.DEV ? 'DEVELOPMENT_FAIL_CLOSED' : 'MISSING_SERVER_PROJECTION',
  }
}

export function getEffectiveAccess(
  catalog: EffectiveAccessCatalog | null | undefined,
  key: string | null,
): EffectiveAccessEntry {
  if (!key) {
    return {
      key: 'public.presentation',
      status: 'AVAILABLE',
      reasonCode: 'NO_ACCESS_KEY_REQUIRED',
      explanation: 'This presentation route does not require an access projection.',
      factors: {
        capability: 'NOT_APPLICABLE',
        runtime: 'NOT_APPLICABLE',
        entitlement: 'NOT_APPLICABLE',
        policy: 'NOT_APPLICABLE',
        quota: 'NOT_APPLICABLE',
      },
      source: 'SERVER',
    }
  }
  return catalog?.[key] ?? unknownAccess(key)
}

export function isAvailable(entry: EffectiveAccessEntry): boolean {
  return entry.status === 'AVAILABLE'
}

const labels: Record<EffectiveAccessStatus, string> = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable',
  NOT_ENTITLED: 'Not entitled',
  POLICY_DENIED: 'Policy denied',
  QUOTA_EXHAUSTED: 'Quota exhausted',
  RUNTIME_UNAVAILABLE: 'Runtime unavailable',
  UNKNOWN_FAIL_CLOSED: 'Access unknown — disabled',
}

export function AccessStatus({ entry, compact = false }: { entry: EffectiveAccessEntry; compact?: boolean }) {
  const tone = entry.status === 'AVAILABLE' ? 'success' : entry.status === 'UNKNOWN_FAIL_CLOSED' ? 'warning' : 'danger'
  return (
    <div className={`ff-access ff-access--${tone}`} role={entry.status === 'AVAILABLE' ? 'status' : 'note'}>
      <strong>{labels[entry.status]}</strong>
      {!compact ? <span>{entry.explanation}</span> : null}
    </div>
  )
}

export function AccessBoundary({ entry, children }: { entry: EffectiveAccessEntry; children: ReactNode }) {
  if (!isAvailable(entry)) return <AccessStatus entry={entry} />
  return <>{children}</>
}
