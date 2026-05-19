import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useArtifact } from './useArtifact'
import type { Artifact } from '@/types'

// @vitest-environment jsdom
const originalOpen = typeof window !== 'undefined' ? window.open : undefined

describe('useArtifact', () => {
  const mockArtifact: Artifact = {
    id: 'art-1',
    renderJobId: 'job-1',
    projectId: 'proj-1',
    name: 'Test Export',
    outputFormat: 'mp4',
    duration: 60,
    fileSize: 1024000,
    provider: 'stub',
    outputUrl: 'https://example.com/video.mp4',
    createdAt: '2024-01-01T00:00:00Z',
  }

  beforeEach(() => {
    window.open = vi.fn() as typeof window.open
  })

  afterEach(() => {
    window.open = originalOpen as typeof window.open
  })

  it('initializes with null artifact', () => {
    const { artifact, isDownloading, downloadError, previewOpen } = useArtifact()
    expect(artifact.value).toBeNull()
    expect(isDownloading.value).toBe(false)
    expect(downloadError.value).toBeNull()
    expect(previewOpen.value).toBe(false)
  })

  it('sets artifact', () => {
    const { artifact, setArtifact } = useArtifact()
    setArtifact(mockArtifact)
    expect(artifact.value).toEqual(mockArtifact)
  })

  it('opens preview', () => {
    const { previewOpen, openPreview } = useArtifact()
    openPreview()
    expect(previewOpen.value).toBe(true)
  })

  it('closes preview', () => {
    const { previewOpen, openPreview, closePreview } = useArtifact()
    openPreview()
    closePreview()
    expect(previewOpen.value).toBe(false)
  })

  it('downloads artifact with URL', async () => {
    const { setArtifact, downloadArtifact } = useArtifact()
    setArtifact(mockArtifact)
    await downloadArtifact()
    expect(window.open).toHaveBeenCalledWith('https://example.com/video.mp4', '_blank')
  })

  it('handles download without URL', async () => {
    const { setArtifact, downloadError, downloadArtifact } = useArtifact()
    setArtifact({ ...mockArtifact, outputUrl: undefined })
    await downloadArtifact()
    expect(downloadError.value).toContain('COMMON-404-001')
  })
})
