import { describe, expect, it } from 'vitest'
import {
  ADD_MEDIA_CLIP_DEFINITION,
  ADD_MEDIA_CLIP_PRESENTATION_VERSION,
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  applyCommandId,
  contentHash,
  exactMediaTime,
  planDigest,
  revisionId,
  sourceContentDigest,
  trackId,
} from './types'

describe('opaque Timeline identifiers and exact media time', () => {
  it('accepts bounded logical identifiers and exact hashes', () => {
    expect(revisionId('revision-R0')).toBe('revision-R0')
    expect(trackId('video:1')).toBe('video:1')
    expect(applyCommandId('apply-1')).toBe('apply-1')
    expect(contentHash('A'.repeat(43) + '=')).toBe('A'.repeat(43) + '=')
    expect(sourceContentDigest('B'.repeat(64))).toBe('b'.repeat(64))
    expect(planDigest('C'.repeat(64))).toBe('c'.repeat(64))
  })

  it.each(['a'.repeat(64), 'A'.repeat(43), 'A'.repeat(42) + 'B=', 'A'.repeat(43) + '==', 'A'.repeat(42) + '-='])
    ('rejects noncanonical Timeline SHA-256 Base64 %s', value => {
      expect(() => contentHash(value)).toThrow()
    })

  it('keeps source and plan Hex digests separate from Timeline Base64', () => {
    expect(() => sourceContentDigest('A'.repeat(43) + '=')).toThrow()
    expect(() => planDigest('A'.repeat(43) + '=')).toThrow()
  })

  it.each(['1.5', '1/0', '1/-2', '', '1/2/3'])('rejects non-canonical rational media time %s', value => {
    expect(() => exactMediaTime(value)).toThrow()
  })

  it.each(['https://host/item', 'space id', '', 'a'.repeat(65)])('rejects non-logical or unbounded identifiers %s', value => {
    expect(() => trackId(value)).toThrow()
  })

  it('keeps the canonical definition identity separate from the preview discriminator', () => {
    expect(ADD_MEDIA_CLIP_DEFINITION).toBe('timeline.media-clip.add')
    expect(ADD_MEDIA_CLIP_PRESENTATION_VERSION).toBe('1.0')
    expect(ADD_MEDIA_CLIP_PREVIEW_OPERATION).toBe('ADD_MEDIA_CLIP_V1')
  })
})
