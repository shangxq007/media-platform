import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import { assetGateway } from '../../api/app/asset.gateway'
import type { SemanticBatch, SemanticStep } from '../../contracts/semantic-batch.generated'
import { TimelineMediaPreview } from './TimelineMediaPreview'
import { artifactId, exactMediaTime, mediaAssetId, mediaStreamId, sourceContentDigest } from './types'

const profile = 'noto-sc-color-emoji-v1'
const one = (numerator: number) => ({ numerator, denominator: 1 })
const cue = (id: string, end: number) => ({
  id, start: one(1), duration: one(end-1), subtitleCue: { fontProfile: profile, keys: [] },
  styledText: { content: 'Rendered subtitle', styleRuns: [{ style: { fontSize: { value: one(24) } } }] },
  frame: { widthConstraint: one(200) },
})
const shortened: SemanticStep = { definitionId: 'timeline.subtitle.cue.set', parameters: {
  cueId: 'end-cue', text: 'Shortened', start: '1/1', end: '5/1', keys: [], fontProfile: profile, width: 200, fontSize: 24,
} }
const removed: SemanticStep = { definitionId: 'timeline.subtitle.cue.remove', parameters: { cueId: 'end-cue' } }
const plan = (step: SemanticStep): SemanticBatch => ({
  batchId: 'confirmed-preview', baseRevisionId: 'r1', baseContentHash: 'a'.repeat(64), operations: [step],
})
const props = { tenantId: 'tenant', projectId: 'project', revisionId: 'r1' }
const digest = 'a'.repeat(64)

describe('N15 F2 effective Timeline media preview extent', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it.each([
    { edit: 'shortens', step: shortened, otherEnd: 6, remaining: 6 },
    { edit: 'removes', step: removed, otherEnd: 6, remaining: 6 },
    { edit: 'removes', step: removed, otherEnd: 3, remaining: 4 },
  ])('$edit the last cue and clamps to the remaining $remaining s composition', async ({step, otherEnd, remaining}) => {
    const sourceDuration = exactMediaTime('4/1')
    vi.spyOn(FormalRenderAPI, 'snapshot').mockResolvedValue({
      revisionId: 'r1', schemaVersion: 'timeline-1.0', canonicalTimelineJson: JSON.stringify({
        schemaVersion: 'timeline-1.0', textElements: [cue('end-cue', 8),cue('other', otherEnd)],
        tracks: [{ trackId: 'track', name: 'Video', type: 'video', clips: [{
          clipId: 'clip', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: digest,
          startTime: '0/1', endTime: '4/1', trimStart: '0/1', trimEnd: '4/1',
          temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
        }] }],
      }),
    })
    const layout = vi.spyOn(FormalRenderAPI, 'subtitleLayout').mockResolvedValue(new Blob(['raster']))
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
    const onTime = vi.fn()
    vi.spyOn(assetGateway, 'listSourcePins').mockResolvedValue({ ok: true, value: [{
      name: 'media', artifactId: artifactId('artifact'), mediaAssetId: mediaAssetId('asset'),
      mediaStreamId: mediaStreamId('stream'), contentDigest: sourceContentDigest(digest), duration: sourceDuration,
      byteLength: 1, streams: [{ streamId: mediaStreamId('stream'), kind: 'VIDEO', duration: sourceDuration }],
    }] })
    vi.spyOn(assetGateway, 'readSourceContent').mockResolvedValue({ ok: true, value: new Blob(['clip']) })
    vi.stubGlobal('createImageBitmap', async () => ({ width: 200, height: 38, close() {} }))
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL() { return 'blob:source' }
      static revokeObjectURL() {}
    })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(function(this: HTMLMediaElement) {
      this.dispatchEvent(new Event('loadeddata'))
    })
    const view = render(<TimelineMediaPreview {...props} onTime={onTime} />)
    await screen.findByText('Preview ready.')
    const seek = screen.getByLabelText<HTMLInputElement>('Timeline preview seek')
    expect(seek.max).toBe('8')
    fireEvent.change(seek,{ target: { value: '7.5' } })
    expect(onTime).toHaveBeenLastCalledWith(7.5)
    view.rerender(<TimelineMediaPreview {...props} onTime={onTime} subtitlePlan={plan(step)} />)
    await waitFor(() => expect(seek.max).toBe(String(remaining)))
    expect(seek.valueAsNumber).toBe(remaining)
    expect(onTime).toHaveBeenLastCalledWith(remaining)
    expect(screen.getByLabelText('Timeline preview time').textContent).toBe(
      `${remaining.toFixed(2)}s / ${remaining.toFixed(2)}s`)
    if (step.definitionId === 'timeline.subtitle.cue.set') await waitFor(() => expect(layout).toHaveBeenCalledTimes(3))
    view.rerender(<TimelineMediaPreview {...props} onTime={onTime} />)
    await waitFor(() => expect(seek.max).toBe('8'))
    expect(seek.valueAsNumber).toBe(remaining)
    expect(apply).not.toHaveBeenCalled()
  })
})
