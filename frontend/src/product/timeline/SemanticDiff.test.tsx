import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { RevisionComparison, RevisionListEntry } from './gateways'
import { revisionId } from './types'
import { SemanticDiff } from './SemanticDiff'

const from: RevisionListEntry = { id: revisionId('revision-R0'), revisionNumber: 1, parentRevisionId: null, source: 'USER', message: null, labels: [], authorUserId: null, createdAt: '2026-01-01', isMerge: false }
const comparison: RevisionComparison = {
  fromRevision: from,
  toRevision: { ...from, id: revisionId('revision-R1'), revisionNumber: 2, parentRevisionId: from.id },
  summary: { supported: true, tracksAdded: 0, tracksRemoved: 0, tracksModified: 1, clipsAdded: 1, clipsRemoved: 0, clipsModified: 0, assetsAdded: 1, assetsRemoved: 0 },
  entityChanges: [
    { kind: 'CLIP', entityId: 'clip-1', action: 'added' },
    { kind: 'TRACK', entityId: 'video-1', action: 'modified' },
  ],
}

describe('server semantic diff presentation', () => {
  it('only formats, groups, and filters server entities', () => {
    const onFilter = vi.fn()
    render(<SemanticDiff comparison={comparison} actionFilter="ALL" onActionFilterChange={onFilter} />)
    expect(screen.getByText('clip-1')).toBeTruthy()
    expect(screen.getByText('video-1')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Action'), { target: { value: 'added' } })
    expect(onFilter).toHaveBeenCalledWith('added')
  })
})
