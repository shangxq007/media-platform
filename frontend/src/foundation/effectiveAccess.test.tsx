import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AccessBoundary, getEffectiveAccess, isAvailable, unknownAccess } from './effectiveAccess'
import { platformClient } from './platformClient'

describe('server-authoritative effective access', () => {
  it('fails missing and unknown access closed', () => {
    const entry = getEffectiveAccess(undefined, 'timeline.operation.apply')
    expect(entry.status).toBe('UNKNOWN_FAIL_CLOSED')
    expect(Object.values(entry.factors)).toEqual(['UNKNOWN', 'UNKNOWN', 'UNKNOWN', 'UNKNOWN', 'UNKNOWN'])
    expect(isAvailable(entry)).toBe(false)
  })

  it('keeps the missing server projection isolated behind the typed client boundary', async () => {
    const catalog = await platformClient.effectiveAccess.getCatalog(['workflow.invoke'])
    expect(catalog['workflow.invoke']).toMatchObject({
      status: 'UNKNOWN_FAIL_CLOSED',
      reasonCode: 'EFFECTIVE_ACCESS_PROJECTION_MISSING',
    })
  })

  it('renders a reusable reason instead of protected content', () => {
    render(<AccessBoundary entry={unknownAccess('workflow.invoke')}><button>Invoke</button></AccessBoundary>)
    expect(screen.getByText('Access unknown — disabled')).toBeTruthy()
    expect(screen.queryByText('Invoke')).toBeNull()
  })

  it('only enables exact AVAILABLE entries', () => {
    expect(isAvailable({ ...unknownAccess('x'), status: 'AVAILABLE' })).toBe(true)
    expect(isAvailable({ ...unknownAccess('x'), status: 'QUOTA_EXHAUSTED' })).toBe(false)
  })
})
