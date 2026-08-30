import { useId, type ReactNode } from 'react'

export type PlatformErrorKind =
  | 'NETWORK'
  | 'AUTHENTICATION'
  | 'POLICY'
  | 'NOT_ENTITLED'
  | 'QUOTA'
  | 'RUNTIME'
  | 'NOT_FOUND'
  | 'CONFLICT_VALIDATION'
  | 'INTERNAL'

export interface PlatformError {
  readonly kind: PlatformErrorKind
  readonly message: string
  readonly correlationId?: string
  readonly retryable: boolean
}

export type AsyncState = 'LOADING' | 'EMPTY' | 'PARTIAL' | 'UNAVAILABLE' | 'BLOCKED' | 'ERROR'

export function classifyPlatformError(error: unknown): PlatformError {
  const candidate = error as { response?: { status?: number; headers?: Record<string, string> }; message?: string }
  const status = candidate?.response?.status
  const kind: PlatformErrorKind =
    status === 401 ? 'AUTHENTICATION'
      : status === 403 ? 'POLICY'
        : status === 404 ? 'NOT_FOUND'
          : status === 409 || status === 422 ? 'CONFLICT_VALIDATION'
            : status === undefined ? 'NETWORK'
              : 'INTERNAL'
  return {
    kind,
    message: candidate?.message ?? 'The application request could not be completed.',
    correlationId: candidate?.response?.headers?.['x-correlation-id'],
    retryable: kind === 'NETWORK' || kind === 'INTERNAL',
  }
}

export function AsyncStatePanel({ state, title, children }: { state: AsyncState; title: string; children: ReactNode }) {
  const titleId = useId()
  return (
    <section className={`ff-state ff-state--${state.toLowerCase()}`} aria-labelledby={titleId} aria-live="polite" aria-busy={state === 'LOADING'}>
      <span className="ff-state__code">{state}</span>
      <h2 id={titleId}>{title}</h2>
      <div>{children}</div>
    </section>
  )
}
