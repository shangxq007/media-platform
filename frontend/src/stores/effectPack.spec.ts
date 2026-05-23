import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useEffectPackStore } from '@/stores/effectPack'

const mockPack = {
  packId: 'builtin-core',
  version: '2.0.0',
  name: 'Core Effects',
  description: 'Built-in',
  author: 'media-platform',
  compatibility: '2.0',
  allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'],
  builtin: true,
  effects: [
    { effectKey: 'video.fade_in', displayName: 'Fade In', category: 'transition', description: '', parameterSchema: {}, defaultValues: { duration: 1.0 }, providerMappings: ['javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
    { effectKey: 'video.blur', displayName: 'Blur', category: 'video', description: '', parameterSchema: {}, defaultValues: { radius: 2.0 }, providerMappings: ['javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
    { effectKey: 'text.subtitle_burn_in', displayName: 'Subtitle', category: 'text', description: '', parameterSchema: {}, defaultValues: {}, providerMappings: ['javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
    { effectKey: 'video.vignette', displayName: 'Vignette', category: 'video', description: '', parameterSchema: {}, defaultValues: { intensity: 0.5 }, providerMappings: ['ofx'], allowedTiers: ['PRO', 'TEAM', 'ENTERPRISE'] },
  ]
}

vi.mock('@/api/index', () => ({
  EffectPackAPI: {
    list: vi.fn(async () => [mockPack]),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn()
  }
}))

describe('EffectPackStore', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    const store = useEffectPackStore()
    await store.loadFromApi()
  })

  it('loads builtin packs from API', () => {
    const store = useEffectPackStore()
    expect(store.builtinPacks.length).toBeGreaterThan(0)
    expect(store.builtinPacks[0].packId).toBe('builtin-core')
  })

  it('provides all effects map', () => {
    const store = useEffectPackStore()
    expect(store.allEffects.size).toBeGreaterThan(0)
    expect(store.allEffects.has('video.fade_in')).toBe(true)
    expect(store.allEffects.has('video.blur')).toBe(true)
    expect(store.allEffects.has('text.subtitle_burn_in')).toBe(true)
  })

  it('creates clip effect with correct structure', () => {
    const store = useEffectPackStore()
    const clipEffect = store.createClipEffect('video.fade_in')
    expect(clipEffect.effectKey).toBe('video.fade_in')
    expect(clipEffect.id).toBeDefined()
    expect(clipEffect.providerPreference).toBeDefined()
    expect(clipEffect.parameters).toBeDefined()
  })

  it('gets effect by key', () => {
    const store = useEffectPackStore()
    const effect = store.getEffect('video.blur')
    expect(effect).toBeDefined()
    expect(effect?.effectKey).toBe('video.blur')
    expect(effect?.category).toBe('video')
  })

  it('returns undefined for unknown effect', () => {
    const store = useEffectPackStore()
    const effect = store.getEffect('nonexistent.effect')
    expect(effect).toBeUndefined()
  })

  it('filters effects by tier', () => {
    const store = useEffectPackStore()
    const freeEffects = store.getEffectsForTier('FREE')
    expect(freeEffects.length).toBeGreaterThan(0)
    freeEffects.forEach(e => {
      expect(e.allowedTiers).toContain('FREE')
    })
  })

  it('vignette requires PRO tier', () => {
    const store = useEffectPackStore()
    const vignette = store.getEffect('video.vignette')
    expect(vignette).toBeDefined()
    expect(vignette?.allowedTiers).not.toContain('FREE')
    expect(vignette?.allowedTiers).toContain('PRO')
  })

  it('all packs list includes builtin and custom', () => {
    const store = useEffectPackStore()
    expect(store.allPacks.length).toBeGreaterThanOrEqual(store.builtinPacks.length)
  })
})
