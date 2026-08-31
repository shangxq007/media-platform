import { describe, expect, it } from 'vitest'
import { gatewayFailure } from './gateway-error'

describe('versionless gateway typed failures', () => {
  it.each([
    ['STALE_BASE_REVISION', 409, 'STALE_BASE_REVISION'],
    ['STALE_TARGET_REF', 409, 'STALE_TARGET_REF'],
    ['PLAN_CHANGED', 409, 'PLAN_CHANGED'],
    ['AUTHORIZATION_DENIED', 403, 'AUTHORIZATION_DENIED'],
    ['UNSUPPORTED_TEMPORAL_STATE', 422, 'UNSUPPORTED'],
    ['SOURCE_REFERENCE_INVALID', 422, 'VALIDATION'],
  ])('maps %s to %s', (serverCode, status, expected) => {
    const failure = gatewayFailure({ response: { status, data: { errorCode: serverCode, detail: 'typed', failures: ['detail'] } } })
    expect(failure.code).toBe(expected)
    expect(failure.details).toEqual(['detail'])
  })

  it.each([
    [{ response: { status: 503, data: {} } }, 'UNAVAILABLE'],
    [{ message: 'offline' }, 'NETWORK'],
    [{ response: { status: 520, data: {} } }, 'UNKNOWN'],
  ])('maps transport category %#', (error, expected) => {
    expect(gatewayFailure(error).code).toBe(expected)
  })
})
