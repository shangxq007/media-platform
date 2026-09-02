import type { GatewayFailure, GatewayFailureCode } from '../../product/timeline/gateways'

const SERVER_FAILURE_CODES = new Set<GatewayFailureCode>([
  'STALE_BASE_REVISION',
  'STALE_TARGET_REF',
  'PLAN_CHANGED',
  'AUTHORIZATION_DENIED',
  'AUTHORIZATION_CONTEXT_MISMATCH',
  'TENANT_CONTEXT_MISMATCH',
])

export function gatewayFailure(error: unknown): GatewayFailure {
  const candidate = error as {
    message?: string
    response?: { status?: number; data?: { errorCode?: string; detail?: string; message?: string; failures?: unknown } }
  }
  const status = candidate.response?.status
  const payload = candidate.response?.data
  const serverCode = payload?.errorCode
  let code: GatewayFailureCode

  if (serverCode && SERVER_FAILURE_CODES.has(serverCode as GatewayFailureCode)) code = serverCode as GatewayFailureCode
  else if (serverCode === 'UNSUPPORTED_TEMPORAL_STATE' || serverCode === 'UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR') code = 'UNSUPPORTED'
  else if (status === 401) code = 'UNAUTHENTICATED'
  else if (status === 403) code = 'AUTHORIZATION_DENIED'
  else if (status === 404) code = 'NOT_FOUND'
  else if (status === 409) code = 'CONFLICT'
  else if (status === 400 || status === 422) code = 'VALIDATION'
  else if (status === 503) code = 'UNAVAILABLE'
  else if (status === undefined) code = 'NETWORK'
  else code = 'UNKNOWN'

  const details = Array.isArray(payload?.failures)
    ? payload.failures.filter((item): item is string => typeof item === 'string')
    : []
  return {
    ok: false,
    code,
    message: payload?.detail ?? payload?.message ?? candidate.message ?? 'The request could not be completed.',
    details,
    retryable: code === 'NETWORK' || code === 'UNAVAILABLE',
  }
}

export function responseValidationFailure(message: string): GatewayFailure {
  return { ok: false, code: 'VALIDATION', message, details: [], retryable: false }
}
