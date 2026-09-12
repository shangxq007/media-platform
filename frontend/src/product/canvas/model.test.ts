import { describe, expect, it } from 'vitest'
import { createCanvasState, fitCanvasBounds, fitCanvasViewport, changeZoom, resetViewport, moveCanvasGroup, recordCanvasEdit, emptyCanvasHistory, restoreCanvasEdit, canvasMarqueeRefs } from './model'

describe('Canvas presentation geometry', () => {
  it('fits all or a single selection without modifying nodes or references', () => {
    const state = createCanvasState('p')
    const all = fitCanvasViewport(state, 800, 600)
    expect(all.viewportX).toBe(10); expect(all.viewportY).toBe(60)
    const selected = fitCanvasViewport(state, 800, 600, ['note-node'])
    expect(selected.viewportX).toBe(-145); expect(selected.viewportY).toBe(-10)
    expect(selected.nodes).toBe(state.nodes); expect(selected.references).toBe(state.references)
    expect(fitCanvasViewport(state, 800, 600, [])).toBe(state)
  })
  it('centers objects with different bounds, and respects the existing minimum even when all cannot fit', () => {
    const state = createCanvasState('p')
    const fitted = fitCanvasBounds(state, 500, 400, [{ x: -100, y: -50, width: 100, height: 80 }, { x: 200, y: 100, width: 300, height: 250 }])
    expect(fitted.zoom).toBe(0.78)
    expect(fitted.viewportX).toBe(94); expect(fitted.viewportY).toBe(83)
    expect(fitCanvasViewport(state, 100, 100).zoom).toBe(0.5)
  })
  it('leaves empty or invalid layouts alone and clamps zoom commands', () => {
    const state = createCanvasState('p'), empty = { ...state, nodes: [] }
    expect(fitCanvasViewport(empty, 800, 600)).toBe(empty)
    expect(fitCanvasViewport(state, 0, 600)).toBe(state)
    expect(fitCanvasBounds(state, 800, 600, [{ x: NaN, y: 0, width: 20, height: 20 }])).toBe(state)
    expect(changeZoom(state, 99).zoom).toBe(2); expect(changeZoom(state, -99).zoom).toBe(0.5)
    expect(resetViewport({ ...state, zoom: 2, viewportX: 80 }).viewportX).toBe(0)
  })
  it('keeps bounded group movement and local undo independent from the camera', () => {
    const before = createCanvasState('p'), after = moveCanvasGroup(before, before.nodes, { x: 5000, y: 50 })
    expect(after.nodes[1].x - after.nodes[0].x).toBe(310)
    expect(after.nodes[1].x).toBe(2000)
    const history = recordCanvasEdit(emptyCanvasHistory(), before, after)
    const fitted = fitCanvasViewport(after, 800, 600)
    expect(recordCanvasEdit(history, after, fitted)).toBe(history)
    const undone = restoreCanvasEdit(fitted, history.undo[0].before)
    expect(undone.nodes).toEqual(before.nodes); expect(undone.viewportX).toBe(fitted.viewportX)
  })
  it('marquee still resolves model order and rejects foreign scope refs', () => {
    const state = createCanvasState('p'), scope = { workspaceId: 'w', projectId: 'p', surfaceId: 'canvas' as const }
    const refs = canvasMarqueeRefs(state.nodes, { x: 0, y: 0 }, { x: 800, y: 600 }, scope, [{ ...scope, workspaceId: 'foreign', kind: 'NODE', localId: 'note-node' }])
    expect(refs.map(ref => ref.localId)).toEqual(['project-node', 'note-node'])
  })
})
