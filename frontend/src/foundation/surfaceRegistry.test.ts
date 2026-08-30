import { describe, expect, it } from 'vitest'
import { detectRegistryConflicts, getSurface, surfaceRegistry } from './surfaceRegistry'

describe('presentation-only surface registry', () => {
  it('covers every frozen product surface with unique IDs and routes', () => {
    expect(surfaceRegistry.map(surface => surface.id)).toEqual([
      'workspace', 'project-overview', 'nle', 'canvas', 'storyboard', 'screenplay',
      'agent', 'workflow', 'recipe', 'review', 'production', 'operations', 'admin', 'developer',
    ])
    expect(detectRegistryConflicts()).toEqual([])
    expect(surfaceRegistry.every(surface => Boolean(surface.displayName && surface.icon && surface.routeTemplate))).toBe(true)
  })

  it('builds an encoded project route without changing its identities', () => {
    expect(getSurface('canvas').buildRoute({ workspaceId: 'workspace one', projectId: 'project/two' }))
      .toBe('/w/workspace%20one/projects/project%2Ftwo/canvas')
  })

  it('keeps placeholders honestly hidden or preview', () => {
    expect(getSurface('storyboard').maturity).toBe('HIDDEN')
    expect(getSurface('production').maturity).toBe('PREVIEW')
    expect(getSurface('nle').requiredBackendCapabilityIds).toContain('operation.apply')
  })
})
