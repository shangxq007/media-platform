import { describe, expect, it } from 'vitest'
import { ArtifactSummary, AccessDescriptor } from './artifact'

describe('artifact application projections', () => {
  it('accepts the complete redacted ArtifactSummary', () => {
    expect(ArtifactSummary.parse({
      id: 'artifact-1',
      type: 'VIDEO',
      kind: 'RENDER_MASTER',
      contentDigest: `SHA_256:${'a'.repeat(64)}`,
      byteLength: 1024,
      state: 'AVAILABLE',
      integrityState: 'DIGEST_RECORDED',
      createdAt: '2026-08-30T00:00:00Z',
    }).id).toBe('artifact-1')
  })

  it('rejects physical coordinates in summary state', () => {
    expect(ArtifactSummary.safeParse({
      id: 'artifact-1',
      type: 'VIDEO',
      kind: 'RENDER_MASTER',
      contentDigest: `SHA_256:${'a'.repeat(64)}`,
      byteLength: 1024,
      state: 'AVAILABLE',
      integrityState: 'DIGEST_RECORDED',
      createdAt: '2026-08-30T00:00:00Z',
      storageUri: 's3://private-bucket/object',
    }).success).toBe(false)
  })

  it('accepts only the ephemeral access shape', () => {
    expect(AccessDescriptor.parse({
      artifactId: 'artifact-1',
      accessUrl: 'https://example.invalid/signed',
      expiresAt: '2026-08-30T00:15:00Z',
    }).artifactId).toBe('artifact-1')
  })
})
