import type { EffectiveAccessCatalog } from './effectiveAccess'
import { getEffectiveAccess, isAvailable } from './effectiveAccess'
import { getSurface, type SurfaceId } from './surfaceRegistry'

export type CommandId =
  | 'navigation.command-palette.open'
  | 'project.create'
  | 'render.open'
  | 'timeline.operation.apply'
  | 'canvas.reference.add'
  | 'workflow.invoke'
  | 'agent.action.authorize'
  | 'review.merge.request'

export interface CommandContext {
  readonly surfaceId: SurfaceId
  readonly hasResolvedProject: boolean
  readonly accessCatalog?: EffectiveAccessCatalog
}

export interface CommandAvailability {
  readonly available: boolean
  readonly reason: string
}

export interface CommandDefinition {
  readonly id: CommandId
  readonly label: string
  readonly defaultShortcut?: string
  readonly projectRequired: boolean
  readonly requiredAccessKey: string | null
  readonly execute: () => void | Promise<void>
}

export type ShortcutOverrides = Readonly<Partial<Record<CommandId, string>>>

function notIntegrated(commandId: CommandId): never {
  throw new Error(`${commandId} is NOT_INTEGRATED; no application command was invoked.`)
}

export const commandRegistry: readonly CommandDefinition[] = [
  { id: 'navigation.command-palette.open', label: 'Open command palette', defaultShortcut: 'Mod+K', projectRequired: false, requiredAccessKey: null, execute: () => undefined },
  { id: 'project.create', label: 'Create project', defaultShortcut: 'Mod+Shift+N', projectRequired: false, requiredAccessKey: 'project.create', execute: () => notIntegrated('project.create') },
  { id: 'render.open', label: 'Open render entry', defaultShortcut: 'Mod+Enter', projectRequired: true, requiredAccessKey: 'render.submit', execute: () => notIntegrated('render.open') },
  { id: 'timeline.operation.apply', label: 'Apply timeline operation', projectRequired: true, requiredAccessKey: 'timeline.operation.apply', execute: () => notIntegrated('timeline.operation.apply') },
  { id: 'canvas.reference.add', label: 'Add canvas reference', projectRequired: true, requiredAccessKey: 'canvas.reference.add', execute: () => notIntegrated('canvas.reference.add') },
  { id: 'workflow.invoke', label: 'Invoke workflow', projectRequired: true, requiredAccessKey: 'workflow.invoke', execute: () => notIntegrated('workflow.invoke') },
  { id: 'agent.action.authorize', label: 'Authorize proposed action', projectRequired: true, requiredAccessKey: 'agent.action.authorize', execute: () => notIntegrated('agent.action.authorize') },
  { id: 'review.merge.request', label: 'Request canonical merge', projectRequired: true, requiredAccessKey: 'timeline.merge', execute: () => notIntegrated('review.merge.request') },
]

export function getCommandAvailability(command: CommandDefinition, context: CommandContext): CommandAvailability {
  const surface = getSurface(context.surfaceId)
  if (surface.maturity === 'HIDDEN') return { available: false, reason: 'This surface is not discoverable at its current maturity.' }
  if (command.projectRequired && !context.hasResolvedProject) return { available: false, reason: 'A server-resolved Project context is required.' }
  const access = getEffectiveAccess(context.accessCatalog, command.requiredAccessKey)
  if (!isAvailable(access)) return { available: false, reason: access.explanation }
  return { available: true, reason: 'Available for invocation; the server will authorize the command again.' }
}

export function getShortcut(command: CommandDefinition, overrides: ShortcutOverrides = {}): string | undefined {
  return overrides[command.id] ?? command.defaultShortcut
}

export function detectShortcutConflicts(
  commands: readonly CommandDefinition[],
  overrides: ShortcutOverrides = {},
): Readonly<Record<string, readonly CommandId[]>> {
  const entries = commands.reduce<Record<string, CommandId[]>>((accumulator, command) => {
    const shortcut = getShortcut(command, overrides)?.toLowerCase()
    if (!shortcut) return accumulator
    accumulator[shortcut] = [...(accumulator[shortcut] ?? []), command.id]
    return accumulator
  }, {})
  return Object.fromEntries(Object.entries(entries).filter(([, ids]) => ids.length > 1))
}

export function useShortcutOverrides(overrides: ShortcutOverrides = {}): ShortcutOverrides {
  return overrides
}
