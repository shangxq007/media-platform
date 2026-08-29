import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ObservabilityDashboard } from './ObservabilityDashboard'
import { SmokeEditorPage } from './SmokeEditorPage'
import { CapabilitiesPage } from '../shared/CapabilitiesPage'

describe('projection-gap product surfaces', () => {
  it('fails render authoring closed without accepting storage coordinates', () => {
    render(<SmokeEditorPage />)
    expect(screen.getByText('Canonical authoring projection required')).toBeTruthy()
    expect(screen.queryByLabelText('Asset URI')).toBeNull()
  })

  it('does not fabricate an effective access catalog', () => {
    render(<CapabilitiesPage />)
    expect(screen.getByText('Effective access projection unavailable')).toBeTruthy()
    expect(screen.queryByText('Render Job')).toBeNull()
  })

  it('does not reconstruct observability from unimplemented endpoints', () => {
    render(<ObservabilityDashboard />)
    expect(screen.getByText('Application projection required')).toBeTruthy()
    expect(screen.queryByText('Provider Fallback')).toBeNull()
  })
})
