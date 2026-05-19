import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtifactPreviewModal from './ArtifactPreviewModal.vue'
import type { Artifact } from '@/types'

const videoArtifact: Artifact = {
  id: 'art-1',
  renderJobId: 'job-1',
  projectId: 'proj-1',
  name: 'Test Video',
  outputFormat: 'mp4',
  duration: 60,
  fileSize: 1024000,
  width: 1920,
  height: 1080,
  provider: 'stub',
  outputUrl: 'https://example.com/video.mp4',
  createdAt: '2024-01-01T00:00:00Z',
}

const audioArtifact: Artifact = {
  id: 'art-2',
  renderJobId: 'job-2',
  projectId: 'proj-1',
  name: 'Test Audio',
  outputFormat: 'mp3',
  duration: 120,
  fileSize: 5120000,
  provider: 'stub',
  outputUrl: 'https://example.com/audio.mp3',
  createdAt: '2024-01-01T00:00:00Z',
}

const imageArtifact: Artifact = {
  id: 'art-3',
  renderJobId: 'job-3',
  projectId: 'proj-1',
  name: 'Test Image',
  outputFormat: 'png',
  duration: 0,
  fileSize: 2048000,
  width: 1920,
  height: 1080,
  provider: 'stub',
  outputUrl: 'https://example.com/image.png',
  createdAt: '2024-01-01T00:00:00Z',
}

function getTeleportedContent() {
  return document.body.innerHTML
}

describe('ArtifactPreviewModal', () => {
  it('does not render when closed', () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: false, artifact: videoArtifact },
    })
    expect(wrapper.find('.fixed').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders when open with video artifact', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const html = getTeleportedContent()
    expect(html).toContain('fixed')
    wrapper.unmount()
  })

  it('shows video element for mp4', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const videos = document.body.querySelectorAll('video')
    expect(videos.length).toBeGreaterThan(0)
    expect(videos[0].getAttribute('src')).toBe('https://example.com/video.mp4')
    wrapper.unmount()
  })

  it('shows audio element for mp3', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: audioArtifact },
    })
    await wrapper.vm.$nextTick()
    const audios = document.body.querySelectorAll('audio')
    expect(audios.length).toBeGreaterThan(0)
    expect(audios[0].getAttribute('src')).toBe('https://example.com/audio.mp3')
    wrapper.unmount()
  })

  it('shows image element for png', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: imageArtifact },
    })
    await wrapper.vm.$nextTick()
    const images = document.body.querySelectorAll('img')
    expect(images.length).toBeGreaterThan(0)
    expect(images[0].getAttribute('src')).toBe('https://example.com/image.png')
    wrapper.unmount()
  })

  it('shows fallback for unsupported format', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: {
        open: true,
        artifact: { ...videoArtifact, outputFormat: 'mkv', outputUrl: undefined },
      },
    })
    await wrapper.vm.$nextTick()
    const html = getTeleportedContent()
    expect(html).toContain('Preview not available for MKV format')
    wrapper.unmount()
  })

  it('shows stub notice for unsupported format', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: {
        open: true,
        artifact: { ...videoArtifact, outputFormat: 'mkv', outputUrl: undefined },
      },
    })
    await wrapper.vm.$nextTick()
    const html = getTeleportedContent()
    expect(html).toContain('Backend render is still stub')
    wrapper.unmount()
  })

  it('displays artifact name in header', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const html = getTeleportedContent()
    expect(html).toContain('Test Video')
    wrapper.unmount()
  })

  it('displays format and resolution in footer', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const html = getTeleportedContent()
    expect(html).toContain('MP4')
    expect(html).toContain('1920')
    wrapper.unmount()
  })

  it('emits close when close button clicked', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const closeBtns = document.body.querySelectorAll('button')
    const closeBtn = Array.from(closeBtns).find(b => b.getAttribute('aria-label') === 'Close preview')
    expect(closeBtn).toBeTruthy()
    await closeBtn!.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('emits close when footer close clicked', async () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: videoArtifact },
    })
    await wrapper.vm.$nextTick()
    const allBtns = document.body.querySelectorAll('button')
    const footerClose = Array.from(allBtns).find(b => b.textContent?.trim() === 'Close')
    expect(footerClose).toBeTruthy()
    await footerClose!.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('renders with null artifact when open', () => {
    const wrapper = mount(ArtifactPreviewModal, {
      props: { open: true, artifact: null },
    })
    expect(wrapper.find('.fixed').exists()).toBe(false)
    wrapper.unmount()
  })
})
