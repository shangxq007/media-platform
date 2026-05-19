import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { EffectPack, EffectPackEffect, ClipEffect } from '@/types'

export const useEffectPackStore = defineStore('effectPack', () => {
  const builtinPacks = ref<EffectPack[]>([])
  const customPacks = ref<EffectPack[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const allPacks = computed(() => [...builtinPacks.value, ...customPacks.value])

  const allEffects = computed(() => {
    const map = new Map<string, EffectPackEffect & { packId: string; packVersion: string }>()
    for (const pack of allPacks.value) {
      for (const effect of pack.effects) {
        map.set(effect.effectKey, { ...effect, packId: pack.packId, packVersion: pack.version })
      }
    }
    return map
  })

  function loadBuiltinPacks() {
    builtinPacks.value = [{
      packId: 'builtin-core',
      version: '2.0.0',
      name: 'Core Effects',
      description: 'Built-in core effects pack',
      author: 'media-platform',
      compatibility: '2.0',
      allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'],
      effects: [
        { effectKey: 'video.fade_in', displayName: 'Fade In', category: 'transition', description: 'Fade from black', parameterSchema: { duration: { type: 'float', defaultValue: 1.0, min: 0.1, max: 5.0, description: 'Duration in seconds' } }, defaultValues: { duration: 1.0 }, providerMappings: ['javacv', 'ofx'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
        { effectKey: 'video.fade_out', displayName: 'Fade Out', category: 'transition', description: 'Fade to black', parameterSchema: { duration: { type: 'float', defaultValue: 1.0, min: 0.1, max: 5.0, description: 'Duration in seconds' } }, defaultValues: { duration: 1.0 }, providerMappings: ['javacv', 'ofx'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
        { effectKey: 'video.cross_dissolve', displayName: 'Cross Dissolve', category: 'transition', description: 'Cross-fade', parameterSchema: { duration: { type: 'float', defaultValue: 0.5, min: 0.1, max: 3.0, description: 'Duration' } }, defaultValues: { duration: 0.5 }, providerMappings: ['ofx', 'javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
        { effectKey: 'video.blur', displayName: 'Blur', category: 'video', description: 'Gaussian blur', parameterSchema: { radius: { type: 'float', defaultValue: 2.0, min: 0.1, max: 10.0, description: 'Blur radius' } }, defaultValues: { radius: 2.0 }, providerMappings: ['ofx', 'javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
        { effectKey: 'video.vignette', displayName: 'Vignette', category: 'video', description: 'Vignette effect', parameterSchema: { intensity: { type: 'float', defaultValue: 0.5, min: 0.0, max: 1.0, description: 'Intensity' } }, defaultValues: { intensity: 0.5 }, providerMappings: ['ofx'], allowedTiers: ['PRO', 'TEAM', 'ENTERPRISE'] },
        { effectKey: 'text.subtitle_burn_in', displayName: 'Subtitle', category: 'text', description: 'Burn subtitles', parameterSchema: { text: { type: 'string', defaultValue: '', description: 'Subtitle text' }, position: { type: 'string', defaultValue: 'bottom', description: 'Position' } }, defaultValues: { text: '', position: 'bottom' }, providerMappings: ['ofx', 'javacv'], allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'] },
      ]
    }]
  }

  function getEffect(effectKey: string): (EffectPackEffect & { packId: string; packVersion: string }) | undefined {
    return allEffects.value.get(effectKey)
  }

  function createClipEffect(effectKey: string): ClipEffect {
    const effect = getEffect(effectKey)
    return {
      id: `ce_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      effectKey,
      packId: effect?.packId,
      packVersion: effect?.packVersion,
      providerPreference: effect?.providerMappings || ['javacv'],
      parameters: { ...(effect?.defaultValues || {}) }
    }
  }

  function getEffectsForTier(tier: string): EffectPackEffect[] {
    return [...allEffects.value.values()].filter(e => e.allowedTiers.includes(tier))
  }

  loadBuiltinPacks()

  return { builtinPacks, customPacks, allPacks, allEffects, loading, error, getEffect, createClipEffect, getEffectsForTier }
})
