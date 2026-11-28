import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { assetGateway } from '../../api/app/asset.gateway'
import { FormalRenderAPI } from '../../api/formal-render'
import { RevisionComparison, saveRevisionComparison } from './RevisionComparison'
import { artifactId, exactMediaTime, mediaAssetId, mediaStreamId, sourceContentDigest } from './types'

vi.mock('../../api/app/timeline-query.gateway', () => ({
  listEditingBranches: async () => ['main'],
  readEditingBranch: async () => ({ ok: true, value: {
    history: ['r1', 'r2'].map((id, index) => ({ id, revisionNumber: 2 - index })),
  } }),
}))

type Snapshot = Awaited<ReturnType<typeof FormalRenderAPI.snapshot>>
interface Request {
  aborted?: AbortSignal
  revision: string
  resolve(value: Snapshot): void
  reject(reason: Error): void
}

const requests: Request[] = []
const scope = 'transport-test'
const digest = 'a'.repeat(64)
const clip = (id: string, start: number, end: number) => ({
  clipId: id, sourceKind: 'MEDIA_STREAM', artifactId: id, mediaStreamId: 'stream', contentDigest: digest,
  startTime: `${start}/1`, endTime: `${end}/1`, trimStart: '0/1', trimEnd: `${end-start}/1`,
  temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
})
const snapshot = (revisionId: string) => ({
  revisionId,
  schemaVersion: 'timeline-1.0',
  canonicalTimelineJson: JSON.stringify({ schemaVersion: 'timeline-1.0', textElements: [], tracks: [
    { trackId: 'video', name: 'Video', type: 'video', clips: [clip('zero', 0, 3), clip('three', 3, 8)] },
  ] }),
})
const btn = (name: string) => screen.getByRole<HTMLButtonElement>('button', { name })
const option = (side: string) => screen.getByLabelText<HTMLSelectElement>(`Saved revision for ${side}`)
const select = (side: string, value: string) => fireEvent.change(option(side), { target: { value } })
const player = () => screen.getByRole('region', { name: 'Timeline media preview' })
const comparison = () => screen.getByRole('region', { name: 'Revision comparison' })
const view = () => ({
  shared: comparison().dataset.reviewPosition,
  cursor: player().dataset.previewCursor,
  clip: player().dataset.activeClip,
  status: player().dataset.previewStatus,
})
const take = (revision: string, index = 0) => requests.filter(item => item.revision === revision)[index]
const finish = (request: Request) => act(async () => request.resolve(snapshot(request.revision)))

async function mount(b: 'r2' | null) {
  saveRevisionComparison(scope, { open: true, selected: 'A', position: '0/1',
    pins: { A: { branch: 'main', revision: 'r1' }, B: b ? { branch: 'main', revision: b } : null },
  })
  render(<RevisionComparison project="project1" tenant="tenant1" editingBranch="main"
    preferenceScope={scope} onClose={() => {}} />)
  await waitFor(() => expect(option('A').querySelector('option[value="r2"]')).not.toBeNull())
  await finish(take('r1'))
  fireEvent.change(screen.getByLabelText<HTMLInputElement>('Exact comparison time (seconds)'), { target: { value: '3' } })
  fireEvent.click(btn('Seek comparison'))
  expect(view()).toEqual({ shared: '3/1', cursor: '3/1', clip: 'three', status: 'READY' })
}

beforeEach(() => {
  requests.length = 0
  sessionStorage.clear()
  vi.spyOn(FormalRenderAPI, 'snapshot').mockImplementation((_, revision, aborted) =>
    new Promise((resolve, reject) => requests.push({ revision, aborted, resolve, reject })))
  vi.spyOn(assetGateway, 'listSourcePins').mockResolvedValue({ ok: true, value: ['zero', 'three'].map(id => ({
    name: id, artifactId: artifactId(id), mediaAssetId: mediaAssetId(id), mediaStreamId: mediaStreamId('stream'),
    contentDigest: sourceContentDigest(digest), duration: exactMediaTime('8/1'), byteLength: 1,
    streams: [{ streamId: mediaStreamId('stream'), kind: 'VIDEO', duration: exactMediaTime('8/1') }],
  })) })
  vi.spyOn(assetGateway, 'readSourceContent').mockResolvedValue({ ok: true, value: new Blob(['video']) })
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL() { return 'blob:source' }
    static revokeObjectURL() {}
  })
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ fillRect: vi.fn() } as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(function(this: HTMLMediaElement) {
    this.dispatchEvent(new Event('loadeddata'))
  })
  // Leave playback between scheduled frames so the mounted cursor assertion is exact.
  vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('mounted comparison transport with controlled snapshot completion', () => {
  it('F1 seeks the second mount of the same revision even when its duration is unchanged', async () => {
    await mount(null)
    select('A', 'r2')
    const late = take('r2')
    select('A', 'r1')
    const remount = take('r1', 1)
    const blockedUntilReady = btn('Play comparison').disabled
    expect(late.aborted?.aborted).toBe(true)
    expect(remount).not.toBe(take('r1'))
    await finish(remount)
    const beforePlay = view()
    fireEvent.click(btn('Play comparison'))
    const playing = view()
    await finish(late)
    expect({ blockedUntilReady, beforePlay, playing, afterLate: view() }).toEqual({
      blockedUntilReady: true,
      beforePlay: { shared: '3/1', cursor: '3/1', clip: 'three', status: 'READY' },
      playing: { shared: '3/1', cursor: '3/1', clip: 'three', status: 'PLAYING' },
      afterLate: { shared: '3/1', cursor: '3/1', clip: 'three', status: 'PLAYING' },
    })
  })

  it.each(['empty', 'failed'] as const)('F2 stops A and clears resume intent for %s B', async unavailable => {
    await mount(unavailable === 'failed' ? 'r2' : null)
    if (unavailable === 'failed') await act(async () => take('r2').reject(new Error('Snapshot denied')))
    fireEvent.click(btn('Play comparison'))
    expect(player().dataset.previewStatus).toBe('PLAYING')
    fireEvent.click(btn('Show B'))
    const control = comparison().querySelector<HTMLButtonElement>('[aria-label="Comparison transport"] button:nth-of-type(3)')
    expect({ text: control?.textContent, disabled: control?.disabled,
      previous: comparison().querySelector<HTMLElement>('[data-preview-revision="r1"]')?.dataset.previewStatus,
      reason: comparison().textContent?.includes(unavailable === 'failed' ? 'Snapshot denied' : 'Choose a saved revision for B'),
    }).toEqual({ text: 'Play comparison', disabled: true, previous: 'PAUSED', reason: true })
    fireEvent.click(btn('Show A'))
    expect(player().dataset.previewStatus).toBe('PAUSED')
    expect(btn('Play comparison').disabled).toBe(false)
  })

  it('F2 keeps a valid loading side pending and only displays Pause after it starts', async () => {
    await mount('r2')
    fireEvent.click(btn('Play comparison'))
    fireEvent.click(btn('Show B'))
    const during = screen.queryByRole('button', { name: 'Pause comparison' })?.textContent
    const pending = screen.queryByText('Playback pending for B.')?.textContent
    const blocked = screen.queryByRole<HTMLButtonElement>('button', { name: 'Play comparison' })?.disabled
    await finish(take('r2'))
    expect(btn('Pause comparison').disabled).toBe(false)
    expect(view().status).toBe('PLAYING')
    expect(view().cursor).toBe(view().shared)
    expect({ during, pending, blocked }).toEqual({ during: undefined, pending: 'Playback pending for B.', blocked: true })
  })

  it('a loading side that finishes after switching away never starts playback', async () => {
    await mount('r2')
    fireEvent.click(btn('Play comparison'))
    fireEvent.click(btn('Show B'))
    fireEvent.click(btn('Show A'))
    const before = view()
    await finish(take('r2'))
    expect(view()).toEqual(before)
    expect(comparison().querySelector<HTMLElement>('[data-preview-revision="r2"]')?.dataset.previewStatus).toBe('READY')
  })
})
