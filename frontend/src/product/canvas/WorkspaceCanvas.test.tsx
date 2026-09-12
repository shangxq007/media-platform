import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProjectContextProvider } from '../../foundation/projectContext'
import { WorkspaceSessionProvider } from '../../foundation/workspaceSession'
import { platformClient } from '../../foundation/platformClient'
import { SelectionProvider, useSelection } from '../../interaction/SelectionContext'
import * as canvasModel from './model'
import { CanvasContent } from './WorkspaceCanvas'

const retirement = vi.hoisted(() => ({ listeners: new Set<() => void>() }))
vi.mock('../../auth/oidcClient', async original => ({ ...await original<typeof import('../../auth/oidcClient')>(), subscribeOidcSessionRetirement: (listener: () => void) => { retirement.listeners.add(listener); return () => retirement.listeners.delete(listener) } }))
function Probe() { return <output data-testid="outer-selection">{useSelection().selectedRefs.length}</output> }
function setup() {
  vi.spyOn(platformClient.workspace, 'getHome').mockImplementation(async id => ({ workspace: { id, name: 'Workspace' }, tenantId: id, recentProjects: [] }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const content = (shown = true, projectId = 'p', workspaceId = 'w') => <StrictMode><QueryClientProvider client={client}><WorkspaceSessionProvider workspaceId={workspaceId} projectId={projectId}><ProjectContextProvider workspaceId={workspaceId} projectId={projectId}><SelectionProvider scope={{ surfaceId: 'review', workspaceId, projectId }}><Probe />{shown ? <CanvasContent /> : <div>Away</div>}</SelectionProvider></ProjectContextProvider></WorkspaceSessionProvider></QueryClientProvider></StrictMode>
  return { ...render(content()), content }
}
const stage = () => screen.getByRole('region', { name: 'Infinite canvas workspace' })
const camera = () => stage().querySelector<HTMLElement>('.ff-canvas-viewport')!.style.transform
const note = () => screen.getByRole('button', { name: 'Local composition note' })
const project = () => screen.getByRole('button', { name: 'Project reference' })
const rect = (width = 800, height = 600) => ({ width, height, left: 0, top: 0, right: width, bottom: height, x: 0, y: 0, toJSON: () => ({}) })
afterEach(() => { cleanup(); retirement.listeners.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('Canvas selection and navigation', () => {
  it('handles an explicitly isolated empty model without enabling fit or inventing objects', () => {
    const initial = canvasModel.createCanvasState('p')
    vi.spyOn(canvasModel, 'createCanvasState').mockReturnValue({ ...initial, nodes: [], edges: [] })
    setup()
    expect(screen.getByText('No local objects')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Fit canvas' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: 'Fit selection' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.keyDown(stage(), { key: 'F', shiftKey: true })
    expect(camera()).toBe('translate(0px, 0px) scale(1)')
  })
  it('keeps selection isolated, provides readonly single/multiple summary, fits selection and clears with focus', () => {
    setup(); vi.spyOn(stage(), 'getBoundingClientRect').mockReturnValue(rect())
    expect((screen.getByRole('button', { name: 'Fit selection' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(project()); fireEvent.click(note(), { ctrlKey: true })
    expect(screen.getByTestId('outer-selection').textContent).toBe('0')
    const inspector = screen.getByRole('complementary', { name: 'Selection inspector' })
    expect(within(inspector).getByText('2 selected objects')).toBeTruthy()
    expect(within(inspector).queryByRole('textbox')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Fit selection' }))
    expect(camera()).toContain('translate(10px, 60px)')
    fireEvent.click(screen.getByRole('button', { name: 'Clear selection' }))
    expect(document.activeElement).toBe(stage())
    expect(screen.queryByRole('complementary')).toBeNull()
    expect(note().getAttribute('aria-pressed')).toBe('false')
  })
  it('fits a single object, resets without editing, and preserves camera during local undo/redo', () => {
    setup(); vi.spyOn(stage(), 'getBoundingClientRect').mockReturnValue(rect())
    fireEvent.click(note()); fireEvent.keyDown(note(), { key: 'ArrowRight' })
    expect(note().style.left).toBe('454px')
    fireEvent.keyDown(stage(), { key: 'f' }); const fitted = camera()
    expect(fitted).toContain('translate(-169px, -10px)')
    fireEvent.keyDown(stage(), { key: 'z', ctrlKey: true })
    expect(note().style.left).toBe('430px'); expect(camera()).toBe(fitted)
    fireEvent.keyDown(stage(), { key: 'Z', ctrlKey: true, shiftKey: true })
    expect(note().style.left).toBe('454px')
    fireEvent.keyDown(stage(), { key: '0' })
    expect(camera()).toBe('translate(0px, 0px) scale(1)')
    expect(note().style.left).toBe('454px')
  })
  it('does not intercept typing, IME, dialogs or background shortcuts during modal focus', () => {
    setup(); fireEvent.click(note()); fireEvent.keyDown(note(), { key: 'ArrowRight' })
    for (const tag of ['input', 'textarea', 'select', 'div']) {
      const input = document.createElement(tag); if (tag === 'div') input.contentEditable = 'true'; stage().append(input)
      expect(fireEvent.keyDown(input, { key: 'z', ctrlKey: true })).toBe(true)
      expect(fireEvent.keyDown(input, { key: 'f' })).toBe(true)
      input.remove()
    }
    expect(fireEvent.keyDown(note(), { key: 'ArrowRight', isComposing: true })).toBe(true)
    const dialog = document.createElement('div'); dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); document.body.append(dialog)
    const before = camera()
    expect(fireEvent.keyDown(stage(), { key: 'z', ctrlKey: true })).toBe(true)
    expect(fireEvent.keyDown(stage(), { key: '0' })).toBe(true)
    expect(camera()).toBe(before); expect(note().style.left).toBe('454px'); dialog.remove()
  })
  it('restores camera in the same project, clears selection on departure, and drops camera for a different project or retired session', () => {
    const view = setup(); fireEvent.keyDown(stage(), { key: 'ArrowLeft' }); fireEvent.click(note())
    const saved = camera()
    view.rerender(view.content(false)); view.rerender(view.content())
    expect(camera()).toBe(saved); expect(note().getAttribute('aria-pressed')).toBe('false')
    view.rerender(view.content(false, 'p2')); view.rerender(view.content(true, 'p'))
    expect(camera()).toBe('translate(0px, 0px) scale(1)')
    fireEvent.click(note()); act(() => retirement.listeners.forEach(listener => listener()))
    expect(screen.queryByRole('region', { name: 'Infinite canvas workspace' })).toBeNull()
    expect(screen.getByText('Canvas context unavailable')).toBeTruthy()
  })
  it('preserves manual center on resize and recomputes an explicit fit using current selection', () => {
    let resize!: () => void, size = rect()
    vi.stubGlobal('ResizeObserver', class { constructor(callback: () => void) { resize = callback } observe() {} disconnect() {} })
    const original = HTMLElement.prototype.getBoundingClientRect
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) { return this.classList.contains('ff-workspace-canvas') ? size : original.call(this) })
    setup(); fireEvent.click(screen.getByRole('button', { name: 'Reset viewport' }))
    size = rect(1000, 700); act(() => resize())
    expect(camera()).toBe('translate(100px, 50px) scale(1)')
    fireEvent.click(note()); fireEvent.click(screen.getByRole('button', { name: 'Fit selection' }))
    size = rect(800, 600); act(() => resize())
    expect(camera()).toBe('translate(-145px, -10px) scale(1)')
  })
  it('preserves marquee and group drag as one local undoable transaction, cancelling in-flight gestures on view changes', () => {
    setup()
    const region = stage()
    const release = vi.fn()
    Object.defineProperties(region, { setPointerCapture: { value: vi.fn() }, releasePointerCapture: { value: release } })
    const pointer = (type: 'pointerDown' | 'pointerMove' | 'pointerUp', target: Element, x: number, y: number) => fireEvent[type](target, { pointerId: 7, pointerType: 'mouse', isPrimary: true, button: 0, clientX: x, clientY: y })
    pointer('pointerDown', region, 0, 0); pointer('pointerMove', region, 800, 600)
    expect(region.querySelector('[data-canvas-marquee]')).toBeTruthy()
    pointer('pointerUp', region, 800, 600)
    expect(project().getAttribute('aria-pressed')).toBe('true'); expect(note().getAttribute('aria-pressed')).toBe('true')
    pointer('pointerDown', project(), 150, 120); pointer('pointerMove', region, 190, 160); pointer('pointerUp', region, 190, 160)
    expect(project().style.left).toBe('160px'); expect(note().style.left).toBe('470px')
    fireEvent.keyDown(region, { key: 'z', ctrlKey: true })
    expect(project().style.left).toBe('120px'); expect(note().style.left).toBe('430px')
    pointer('pointerDown', project(), 150, 120); pointer('pointerMove', region, 250, 220)
    fireEvent.click(screen.getByRole('button', { name: 'Reset viewport' }))
    pointer('pointerUp', region, 250, 220)
    expect(project().style.left).toBe('120px'); expect(release).toHaveBeenCalled()
  })
})
