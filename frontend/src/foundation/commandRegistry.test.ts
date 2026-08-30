import { describe, expect, it } from 'vitest'
import { commandRegistry, detectShortcutConflicts, getCommandAvailability } from './commandRegistry'

describe('command availability registry', () => {
  it('blocks project commands without resolved Project context', () => {
    const command = commandRegistry.find(candidate => candidate.id === 'timeline.operation.apply')!
    expect(getCommandAvailability(command, { surfaceId: 'nle', hasResolvedProject: false }).available).toBe(false)
  })

  it('does not let surface maturity or navigation bypass access', () => {
    const command = commandRegistry.find(candidate => candidate.id === 'project.create')!
    const availability = getCommandAvailability(command, { surfaceId: 'workspace', hasResolvedProject: false })
    expect(availability.available).toBe(false)
    expect(availability.reason).toContain('disabled')
  })

  it('detects default and user-override shortcut conflicts', () => {
    expect(detectShortcutConflicts(commandRegistry)).toEqual({})
    expect(detectShortcutConflicts(commandRegistry, { 'project.create': 'Mod+K' })).toEqual({
      'mod+k': ['navigation.command-palette.open', 'project.create'],
    })
  })
})
