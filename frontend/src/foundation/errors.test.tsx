import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AsyncStatePanel, classifyPlatformError } from './errors'

describe('typed asynchronous and error states', () => {
  it('separates authentication, policy, not-found, conflict, network, and internal failures', () => {
    expect(classifyPlatformError({ response: { status: 401 } }).kind).toBe('AUTHENTICATION')
    expect(classifyPlatformError({ response: { status: 403 } }).kind).toBe('POLICY')
    expect(classifyPlatformError({ response: { status: 404 } }).kind).toBe('NOT_FOUND')
    expect(classifyPlatformError({ response: { status: 409 } }).kind).toBe('CONFLICT_VALIDATION')
    expect(classifyPlatformError(new Error('offline')).kind).toBe('NETWORK')
    expect(classifyPlatformError({ response: { status: 500 } }).kind).toBe('INTERNAL')
  })

  it('announces loading, unavailable, blocked, and error presentation states', () => {
    const view = render(<AsyncStatePanel state="LOADING" title="Loading"><p>Waiting</p></AsyncStatePanel>)
    expect(screen.getByRole('region').getAttribute('aria-busy')).toBe('true')
    view.rerender(<AsyncStatePanel state="BLOCKED" title="Blocked"><p>Canonical command required</p></AsyncStatePanel>)
    expect(screen.getByText('Canonical command required')).toBeTruthy()
    view.rerender(<AsyncStatePanel state="UNAVAILABLE" title="Unavailable"><p>Projection missing</p></AsyncStatePanel>)
    expect(screen.getByText('Projection missing')).toBeTruthy()
    view.rerender(<AsyncStatePanel state="ERROR" title="Error"><p>Retry safely</p></AsyncStatePanel>)
    expect(screen.getByText('Retry safely')).toBeTruthy()
  })
})
