import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtifactResult from './ArtifactResult.vue'
import type { Artifact } from '@/types'

const mockArtifact: Artifact = {
  id: 'art-1',
  renderJobId: 'job-1',
  projectId: 'proj-1',
  name: 'Test Export',
  outputFormat: 'mp4',
  duration: 90,
  fileSize: 15728640,
  width: 1920,
  height: 1080,
  provider: 'stub',
  outputUrl: 'https://example.com/video.mp4',
  thumbnailUrl: 'https://example.com/thumb.jpg',
  catalogId: 'cat-1',
  renderLogsUrl: 'https://example.com/logs.txt',
  createdAt: '2024-01-15T10:30:00Z',
}

describe('ArtifactResult', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    })
  })

  it('renders artifact name', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('Test Export')
  })

  it('renders format badge', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('MP4')
  })

  it('formats file size in MB', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('15.0 MB')
  })

  it('formats file size in KB', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, fileSize: 5120 } },
    })
    expect(wrapper.text()).toContain('5.0 KB')
  })

  it('formats file size in B', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, fileSize: 500 } },
    })
    expect(wrapper.text()).toContain('500 B')
  })

  it('formats duration as mm:ss', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('1:30')
  })

  it('shows resolution when available', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('1920×1080')
  })

  it('shows provider', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('stub')
  })

  it('shows completed badge', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('Completed')
  })

  it('emits preview event', async () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    const buttons = wrapper.findAll('button')
    const previewBtn = buttons.find(b => b.text().includes('Preview'))
    expect(previewBtn).toBeTruthy()
    await previewBtn!.trigger('click')
    expect(wrapper.emitted('preview')).toBeTruthy()
  })

  it('emits download event', async () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    const buttons = wrapper.findAll('button')
    const downloadBtn = buttons.find(b => b.text().includes('Download'))
    expect(downloadBtn).toBeTruthy()
    await downloadBtn!.trigger('click')
    expect(wrapper.emitted('download')).toBeTruthy()
  })

  it('shows catalog link when catalogId present', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('Open in Catalog')
  })

  it('hides catalog link when no catalogId', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, catalogId: undefined } },
    })
    expect(wrapper.text()).not.toContain('Open in Catalog')
  })

  it('shows render logs link when URL present', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('Render Logs')
  })

  it('shows video icon for mp4', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: mockArtifact },
    })
    expect(wrapper.text()).toContain('film')
  })

  it('shows audio icon for mp3', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, outputFormat: 'mp3' } },
    })
    expect(wrapper.text()).toContain('music')
  })

  it('shows image icon for png', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, outputFormat: 'png' } },
    })
    expect(wrapper.text()).toContain('image')
  })

  it('hides render logs link when no URL', () => {
    const wrapper = mount(ArtifactResult, {
      props: { artifact: { ...mockArtifact, renderLogsUrl: undefined } },
    })
    expect(wrapper.text()).not.toContain('Render Logs')
  })
})
