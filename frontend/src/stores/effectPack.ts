import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { EffectPack, EffectPackEffect, ClipEffect } from '@/types'
import { EffectPackAPI } from '@/api/index'

export const useEffectPackStore = defineStore('effectPack', () => {
  const builtinPacks = ref<EffectPack[]>([])
  const customPacks = ref<EffectPack[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const allowedPackIds = ref<string[]>([])

  const allPacks = computed(() => [...builtinPacks.value, ...customPacks.value])

  const allEffects = computed(() => {
    const map = new Map<string, EffectPackEffect & { packId: string; packVersion: string }>()
    for (const pack of allPacks.value) {
      if (allowedPackIds.value.length > 0 && !allowedPackIds.value.includes(pack.packId)) {
        continue
      }
      for (const effect of pack.effects) {
        map.set(effect.effectKey, { ...effect, packId: pack.packId, packVersion: pack.version })
      }
    }
    return map
  })

  async function loadFromApi() {
    loading.value = true
    error.value = null
    try {
      const packs = await EffectPackAPI.list()
      builtinPacks.value = packs.filter(p => p.builtin)
      customPacks.value = packs.filter(p => !p.builtin)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load effect packs'
      loadBuiltinFallback()
    } finally {
      loading.value = false
    }
  }

  function setAllowedPackIds(packIds: string[]) {
    allowedPackIds.value = packIds
  }

  function loadBuiltinFallback() {
    builtinPacks.value = [{
      packId: 'builtin-core',
      version: '2.0.0',
      name: 'Core Effects',
      description: 'Built-in core effects pack',
      author: 'media-platform',
      compatibility: '2.0',
      allowedTiers: ['FREE', 'PRO', 'TEAM', 'ENTERPRISE'],
      effects: []
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

  async function saveCustomPack(pack: EffectPack): Promise<EffectPack> {
    const saved = await EffectPackAPI.create(pack)
    const idx = customPacks.value.findIndex(p => p.packId === saved.packId && p.version === saved.version)
    if (idx >= 0) {
      customPacks.value[idx] = saved
    } else {
      customPacks.value.push(saved)
    }
    return saved
  }

  async function updateCustomPack(packId: string, version: string, pack: EffectPack): Promise<EffectPack> {
    const saved = await EffectPackAPI.update(packId, version, pack)
    const idx = customPacks.value.findIndex(p => p.packId === packId && p.version === version)
    if (idx >= 0) {
      customPacks.value[idx] = saved
    }
    return saved
  }

  async function deleteCustomPack(packId: string, version: string): Promise<void> {
    await EffectPackAPI.remove(packId, version)
    customPacks.value = customPacks.value.filter(p => !(p.packId === packId && p.version === version))
  }

  return {
    builtinPacks,
    customPacks,
    allPacks,
    allEffects,
    loading,
    error,
    allowedPackIds,
    loadFromApi,
    setAllowedPackIds,
    getEffect,
    createClipEffect,
    getEffectsForTier,
    saveCustomPack,
    updateCustomPack,
    deleteCustomPack
  }
})
